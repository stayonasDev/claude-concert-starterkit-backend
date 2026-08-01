package starters.springboot.claude.starterkit.payment.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.Transactional;
import starters.springboot.claude.starterkit.common.lock.LockStrategyType;
import starters.springboot.claude.starterkit.concert.domain.Concert;
import starters.springboot.claude.starterkit.concert.domain.Seat;
import starters.springboot.claude.starterkit.concert.domain.SeatGrade;
import starters.springboot.claude.starterkit.concert.repository.ConcertRepository;
import starters.springboot.claude.starterkit.concert.repository.SeatGradeRepository;
import starters.springboot.claude.starterkit.concert.repository.SeatRepository;
import starters.springboot.claude.starterkit.payment.domain.PaymentMethod;
import starters.springboot.claude.starterkit.payment.dto.PaymentCreateRequest;
import starters.springboot.claude.starterkit.reservation.service.ReservationFacade;
import starters.springboot.claude.starterkit.reservation.service.ReservationResult;
import starters.springboot.claude.starterkit.reservation.service.ReserveSeatsCommand;
import starters.springboot.claude.starterkit.support.ContainerTestSupport;
import starters.springboot.claude.starterkit.user.domain.Role;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Transactional
class PaymentControllerTest extends ContainerTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ReservationFacade reservationFacade;

    @Autowired
    private ConcertRepository concertRepository;

    @Autowired
    private SeatGradeRepository seatGradeRepository;

    @Autowired
    private SeatRepository seatRepository;

    private Long reservationId;

    @BeforeEach
    void setUp() {
        LocalDateTime now = LocalDateTime.now();
        Concert concert = concertRepository.save(Concert.builder()
                .title("2026 World Tour")
                .venue("잠실 올림픽 주경기장")
                .performanceAt(now.plusDays(30))
                .bookingOpenAt(now.minusDays(1))
                .bookingCloseAt(now.plusDays(29))
                .build());

        SeatGrade seatGrade = seatGradeRepository.save(SeatGrade.builder()
                .concertId(concert.getId())
                .gradeName("VIP")
                .price(BigDecimal.valueOf(200000))
                .totalCount(10)
                .build());

        Long seatId = seatRepository.save(Seat.builder()
                .concertId(concert.getId())
                .seatGradeId(seatGrade.getId())
                .seatNumber("A-1")
                .build()).getId();

        ReservationResult result = reservationFacade.reserve(new ReserveSeatsCommand(
                1L, concert.getId(), List.of(seatId), LockStrategyType.REDIS));
        this.reservationId = result.reservationId();

        // 결제 실패 시 PaymentFailureService가 REQUIRES_NEW로 별도 트랜잭션에 즉시 커밋하는데,
        // 이 테스트는 클래스 레벨 @Transactional로 setUp()까지 하나의(아직 커밋 안 된) 트랜잭션
        // 안에서 실행된다. 여기서 커밋해 실제 운영(좌석 선점과 결제가 서로 다른 HTTP 요청)과
        // 같은 조건을 만든다.
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();
    }

    @Test
    void 결제_요청시_200과_함께_PAID_상태를_반환한다() throws Exception {
        PaymentCreateRequest request = new PaymentCreateRequest(reservationId, PaymentMethod.MOCK, false);

        mockMvc.perform(post("/api/v1/payments")
                        .header("Authorization", bearerToken(1L, Role.USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("PAID"));
    }

    @Test
    void 결제_실패를_강제하면_PAYMENT_FAILED_에러코드를_200으로_반환한다() throws Exception {
        PaymentCreateRequest request = new PaymentCreateRequest(reservationId, PaymentMethod.MOCK, true);

        mockMvc.perform(post("/api/v1/payments")
                        .header("Authorization", bearerToken(1L, Role.USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("PAYMENT_FAILED"));
    }
}

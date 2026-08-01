package starters.springboot.claude.starterkit.reservation.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import starters.springboot.claude.starterkit.common.lock.LockStrategyType;
import starters.springboot.claude.starterkit.concert.domain.Concert;
import starters.springboot.claude.starterkit.concert.domain.Seat;
import starters.springboot.claude.starterkit.concert.domain.SeatGrade;
import starters.springboot.claude.starterkit.concert.repository.ConcertRepository;
import starters.springboot.claude.starterkit.concert.repository.SeatGradeRepository;
import starters.springboot.claude.starterkit.concert.repository.SeatRepository;
import starters.springboot.claude.starterkit.payment.domain.PaymentMethod;
import starters.springboot.claude.starterkit.payment.service.PaymentCommand;
import starters.springboot.claude.starterkit.payment.service.PaymentService;
import starters.springboot.claude.starterkit.reservation.dto.ReservationCreateRequest;
import starters.springboot.claude.starterkit.reservation.service.ReservationFacade;
import starters.springboot.claude.starterkit.reservation.service.ReserveSeatsCommand;
import starters.springboot.claude.starterkit.support.ContainerTestSupport;
import starters.springboot.claude.starterkit.user.domain.Role;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Transactional
class ReservationControllerTest extends ContainerTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ConcertRepository concertRepository;

    @Autowired
    private SeatGradeRepository seatGradeRepository;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private ReservationFacade reservationFacade;

    @Autowired
    private PaymentService paymentService;

    private Long openConcertId;
    private Long seatId;
    private Long upcomingConcertId;
    private Long closedConcertId;

    @BeforeEach
    void setUp() {
        LocalDateTime now = LocalDateTime.now();

        Concert openConcert = concertRepository.save(Concert.builder()
                .title("2026 World Tour")
                .venue("잠실 올림픽 주경기장")
                .performanceAt(now.plusDays(30))
                .bookingOpenAt(now.minusDays(1))
                .bookingCloseAt(now.plusDays(29))
                .build());
        this.openConcertId = openConcert.getId();

        SeatGrade seatGrade = seatGradeRepository.save(SeatGrade.builder()
                .concertId(openConcert.getId())
                .gradeName("VIP")
                .price(BigDecimal.valueOf(200000))
                .totalCount(10)
                .build());

        this.seatId = seatRepository.save(Seat.builder()
                .concertId(openConcert.getId())
                .seatGradeId(seatGrade.getId())
                .seatNumber("A-1")
                .build()).getId();

        Concert upcomingConcert = concertRepository.save(Concert.builder()
                .title("2026 Winter Tour")
                .venue("고척 스카이돔")
                .performanceAt(now.plusDays(60))
                .bookingOpenAt(now.plusDays(10))
                .bookingCloseAt(now.plusDays(59))
                .build());
        this.upcomingConcertId = upcomingConcert.getId();

        Concert closedConcert = concertRepository.save(Concert.builder()
                .title("2025 Farewell Tour")
                .venue("KSPO DOME")
                .performanceAt(now.minusDays(1))
                .bookingOpenAt(now.minusDays(30))
                .bookingCloseAt(now.minusDays(2))
                .build());
        this.closedConcertId = closedConcert.getId();
    }

    @Test
    void Redis_락_엔드포인트로_좌석을_선점하면_201을_반환한다() throws Exception {
        ReservationCreateRequest request = new ReservationCreateRequest(openConcertId, List.of(seatId));

        mockMvc.perform(post("/api/v1/reservations/redis-lock")
                        .header("Authorization", bearerToken(1L, Role.USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("HOLDING"))
                .andExpect(jsonPath("$.data.lockStrategy").value("REDIS"))
                .andExpect(jsonPath("$.data.seats[0].seatId").value(seatId));
    }

    @Test
    void 예매_오픈_전_콘서트는_403과_에러코드를_반환한다() throws Exception {
        ReservationCreateRequest request = new ReservationCreateRequest(upcomingConcertId, List.of(1L));

        mockMvc.perform(post("/api/v1/reservations/pessimistic-lock")
                        .header("Authorization", bearerToken(1L, Role.USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("BOOKING_NOT_OPEN"));
    }

    @Test
    void 예매_마감된_콘서트는_403과_BOOKING_CLOSED를_반환한다() throws Exception {
        ReservationCreateRequest request = new ReservationCreateRequest(closedConcertId, List.of(1L));

        mockMvc.perform(post("/api/v1/reservations/redis-lock")
                        .header("Authorization", bearerToken(1L, Role.USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("BOOKING_CLOSED"));
    }

    @Test
    void 좌석_목록이_비어있으면_400을_반환한다() throws Exception {
        ReservationCreateRequest request = new ReservationCreateRequest(openConcertId, List.of());

        mockMvc.perform(post("/api/v1/reservations/redis-lock")
                        .header("Authorization", bearerToken(1L, Role.USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void 확정된_예약을_취소하면_200과_CANCELLED_상태를_반환한다() throws Exception {
        Long reservationId = reservationFacade.reserve(new ReserveSeatsCommand(
                1L, openConcertId, List.of(seatId), LockStrategyType.REDIS)).reservationId();
        paymentService.pay(new PaymentCommand(reservationId, PaymentMethod.MOCK, false));

        mockMvc.perform(delete("/api/v1/reservations/{reservationId}", reservationId)
                        .header("Authorization", bearerToken(1L, Role.USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @Test
    void 본인_소유가_아닌_예약을_취소하면_403을_반환한다() throws Exception {
        Long reservationId = reservationFacade.reserve(new ReserveSeatsCommand(
                1L, openConcertId, List.of(seatId), LockStrategyType.REDIS)).reservationId();
        paymentService.pay(new PaymentCommand(reservationId, PaymentMethod.MOCK, false));

        mockMvc.perform(delete("/api/v1/reservations/{reservationId}", reservationId)
                        .header("Authorization", bearerToken(2L, Role.USER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_RESERVATION_OWNER"));
    }

    @Test
    void 본인_예약_목록을_결제_요약과_함께_조회한다() throws Exception {
        Long reservationId = reservationFacade.reserve(new ReserveSeatsCommand(
                1L, openConcertId, List.of(seatId), LockStrategyType.REDIS)).reservationId();
        paymentService.pay(new PaymentCommand(reservationId, PaymentMethod.MOCK, false));

        mockMvc.perform(get("/api/v1/reservations")
                        .header("Authorization", bearerToken(1L, Role.USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].reservationId").value(reservationId))
                .andExpect(jsonPath("$.data[0].status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data[0].seats[0].seatId").value(seatId))
                .andExpect(jsonPath("$.data[0].payment.status").value("PAID"));
    }

    @Test
    void 타인의_예약은_목록에_포함되지_않는다() throws Exception {
        reservationFacade.reserve(new ReserveSeatsCommand(
                1L, openConcertId, List.of(seatId), LockStrategyType.REDIS));

        mockMvc.perform(get("/api/v1/reservations")
                        .header("Authorization", bearerToken(2L, Role.USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }
}

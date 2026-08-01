package starters.springboot.claude.starterkit.queue.interceptor;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import starters.springboot.claude.starterkit.concert.domain.Concert;
import starters.springboot.claude.starterkit.concert.domain.Seat;
import starters.springboot.claude.starterkit.concert.domain.SeatGrade;
import starters.springboot.claude.starterkit.concert.repository.ConcertRepository;
import starters.springboot.claude.starterkit.concert.repository.SeatGradeRepository;
import starters.springboot.claude.starterkit.concert.repository.SeatRepository;
import starters.springboot.claude.starterkit.queue.dto.QueueEnterResponse;
import starters.springboot.claude.starterkit.queue.infra.RedisWaitingQueueRepository;
import starters.springboot.claude.starterkit.reservation.dto.ReservationCreateRequest;
import starters.springboot.claude.starterkit.support.ContainerTestSupport;
import starters.springboot.claude.starterkit.user.domain.Role;
import tools.jackson.databind.ObjectMapper;

/**
 * docs/architecture.md 4장 대응. app.queue.enabled=true로 오버라이드해 대기열 활성화 시나리오만 검증한다
 * (기본값 false인 다른 모든 테스트는 대기열 없이 예약 플로우를 검증하므로 별도 컨텍스트로 분리).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.queue.enabled=true")
@Transactional
class QueueAdmissionInterceptorTest extends ContainerTestSupport {

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
    private RedisWaitingQueueRepository queueRepository;

    private Long concertId;
    private Long seatId;

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
        this.concertId = concert.getId();

        SeatGrade seatGrade = seatGradeRepository.save(SeatGrade.builder()
                .concertId(concertId)
                .gradeName("VIP")
                .price(BigDecimal.valueOf(200000))
                .totalCount(10)
                .build());

        this.seatId = seatRepository.save(Seat.builder()
                .concertId(concertId)
                .seatGradeId(seatGrade.getId())
                .seatNumber("A-1")
                .build()).getId();
    }

    @Test
    void 입장권_없이_예약을_요청하면_403과_QUEUE_REQUIRED를_반환한다() throws Exception {
        ReservationCreateRequest request = new ReservationCreateRequest(concertId, List.of(seatId));

        mockMvc.perform(post("/api/v1/reservations/redis-lock")
                        .header("Authorization", bearerToken(1L, Role.USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("QUEUE_REQUIRED"));
    }

    @Test
    void 유효한_입장권이_있으면_예약_요청이_통과된다() throws Exception {
        // 대기열에 진입시킨 뒤 즉시 승급 처리(스케줄러 대기 없이 직접 호출)
        QueueEnterResponse entered = enter(concertId);
        queueRepository.promote(concertId, 10, Duration.ofMinutes(10));

        ReservationCreateRequest request = new ReservationCreateRequest(concertId, List.of(seatId));

        mockMvc.perform(post("/api/v1/reservations/redis-lock")
                        .header("Authorization", bearerToken(1L, Role.USER))
                        .header("X-Concert-Id", String.valueOf(concertId))
                        .header("X-Queue-Token", entered.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    void 입장권이_만료되면_403과_QUEUE_TOKEN_EXPIRED를_반환한다() throws Exception {
        QueueEnterResponse entered = enter(concertId);
        // admissionTtl을 음수로 주어 이미 만료된 admitted 항목을 만든다 (스케줄러의 evictExpired()가
        // 실행되기 전이므로 QUEUE_TOKEN_EXPIRED로 판별 가능한 짧은 구간을 결정적으로 재현).
        queueRepository.promote(concertId, 10, Duration.ofMinutes(-10));

        ReservationCreateRequest request = new ReservationCreateRequest(concertId, List.of(seatId));

        mockMvc.perform(post("/api/v1/reservations/redis-lock")
                        .header("Authorization", bearerToken(1L, Role.USER))
                        .header("X-Concert-Id", String.valueOf(concertId))
                        .header("X-Queue-Token", entered.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("QUEUE_TOKEN_EXPIRED"));
    }

    private QueueEnterResponse enter(long concertId) {
        String token = java.util.UUID.randomUUID().toString();
        queueRepository.enqueue(concertId, token);
        long rank = queueRepository.rankOf(concertId, token);
        return new QueueEnterResponse(token, rank, 0);
    }
}

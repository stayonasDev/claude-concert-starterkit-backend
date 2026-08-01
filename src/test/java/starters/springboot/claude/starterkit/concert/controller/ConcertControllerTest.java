package starters.springboot.claude.starterkit.concert.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import starters.springboot.claude.starterkit.concert.domain.Concert;
import starters.springboot.claude.starterkit.concert.domain.Seat;
import starters.springboot.claude.starterkit.concert.domain.SeatGrade;
import starters.springboot.claude.starterkit.concert.repository.ConcertRepository;
import starters.springboot.claude.starterkit.concert.repository.SeatGradeRepository;
import starters.springboot.claude.starterkit.concert.repository.SeatRepository;
import starters.springboot.claude.starterkit.support.ContainerTestSupport;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Transactional
class ConcertControllerTest extends ContainerTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ConcertRepository concertRepository;

    @Autowired
    private SeatGradeRepository seatGradeRepository;

    @Autowired
    private SeatRepository seatRepository;

    private Long concertId;

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

        seatRepository.save(Seat.builder()
                .concertId(concertId)
                .seatGradeId(seatGrade.getId())
                .seatNumber("A-1")
                .build());
    }

    @Test
    void 콘서트_목록을_조회한다() throws Exception {
        mockMvc.perform(get("/api/v1/concerts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void 콘서트_상세를_조회한다() throws Exception {
        mockMvc.perform(get("/api/v1/concerts/{concertId}", concertId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("2026 World Tour"))
                .andExpect(jsonPath("$.data.seatGrades[0].gradeName").value("VIP"));
    }

    @Test
    void 존재하지_않는_콘서트_조회시_404를_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/concerts/{concertId}", 999999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("CONCERT_NOT_FOUND"));
    }

    @Test
    void 좌석맵을_조회한다() throws Exception {
        mockMvc.perform(get("/api/v1/concerts/{concertId}/seats", concertId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].seatNumber").value("A-1"))
                .andExpect(jsonPath("$.data[0].status").value("AVAILABLE"));
    }
}

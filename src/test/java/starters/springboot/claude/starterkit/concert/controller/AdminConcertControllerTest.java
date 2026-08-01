package starters.springboot.claude.starterkit.concert.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import starters.springboot.claude.starterkit.concert.dto.ConcertCreateRequest;
import starters.springboot.claude.starterkit.concert.dto.SeatBulkCreateRequest;
import starters.springboot.claude.starterkit.concert.dto.SeatGradeCreateRequest;
import starters.springboot.claude.starterkit.concert.repository.SeatGradeRepository;
import starters.springboot.claude.starterkit.support.ContainerTestSupport;
import starters.springboot.claude.starterkit.user.domain.Role;
import tools.jackson.databind.ObjectMapper;

/**
 * docs/use-cases.md UC-12 대응.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Transactional
class AdminConcertControllerTest extends ContainerTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SeatGradeRepository seatGradeRepository;

    private ConcertCreateRequest concertCreateRequest() {
        LocalDateTime now = LocalDateTime.now();
        return new ConcertCreateRequest(
                "2026 World Tour", "설명", "잠실 올림픽 주경기장",
                now.plusDays(30), now.minusDays(1), now.plusDays(29), null);
    }

    @Test
    void 관리자는_콘서트를_등록할_수_있다() throws Exception {
        mockMvc.perform(post("/api/v1/admin/concerts")
                        .header("Authorization", bearerToken(1L, Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(concertCreateRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.title").value("2026 World Tour"));
    }

    @Test
    void 일반_사용자는_관리자_API에_접근할_수_없다() throws Exception {
        mockMvc.perform(post("/api/v1/admin/concerts")
                        .header("Authorization", bearerToken(1L, Role.USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(concertCreateRequest())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));
    }

    @Test
    void 토큰_없이_관리자_API에_접근하면_401을_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/admin/concerts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(concertCreateRequest())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void 좌석_등급과_좌석을_일괄_등록할_수_있다() throws Exception {
        String createConcertResponse = mockMvc.perform(post("/api/v1/admin/concerts")
                        .header("Authorization", bearerToken(1L, Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(concertCreateRequest())))
                .andReturn().getResponse().getContentAsString();
        Long concertId = ((Number) JsonPath.read(createConcertResponse, "$.data.id")).longValue();

        List<SeatGradeCreateRequest> seatGradeRequests =
                List.of(new SeatGradeCreateRequest("VIP", BigDecimal.valueOf(200000), 10));

        mockMvc.perform(post("/api/v1/admin/concerts/{concertId}/seat-grades", concertId)
                        .header("Authorization", bearerToken(1L, Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(seatGradeRequests)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data[0].gradeName").value("VIP"));

        // 등록 API 응답에는 id가 노출되지 않으므로(공개 조회 DTO 재사용), 벌크 좌석 생성에 필요한
        // seatGradeId는 리포지토리로 직접 조회한다.
        Long seatGradeId = seatGradeRepository.findByConcertId(concertId).get(0).getId();
        SeatBulkCreateRequest bulkRequest = new SeatBulkCreateRequest(seatGradeId, List.of("A-1", "A-2", "A-3"));

        mockMvc.perform(post("/api/v1/admin/concerts/{concertId}/seats/bulk", concertId)
                        .header("Authorization", bearerToken(1L, Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bulkRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data").value(3));
    }
}

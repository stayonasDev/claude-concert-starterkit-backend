package starters.springboot.claude.starterkit.queue.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import starters.springboot.claude.starterkit.support.ContainerTestSupport;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class QueueControllerTest extends ContainerTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 대기열_진입시_토큰과_순번을_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/queue/{concertId}/enter", 8001L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.rank").value(1));
    }

    @Test
    void 진입_후_상태_조회시_대기중_상태를_반환한다() throws Exception {
        String response = mockMvc.perform(post("/api/v1/queue/{concertId}/enter", 8002L))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(response, "$.data.token");

        mockMvc.perform(get("/api/v1/queue/{concertId}/status", 8002L).param("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("WAITING"))
                .andExpect(jsonPath("$.data.rank").value(1));
    }

    @Test
    void 존재하지_않는_토큰으로_상태_조회시_404를_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/queue/{concertId}/status", 8003L).param("token", "invalid-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("QUEUE_TOKEN_NOT_FOUND"));
    }
}

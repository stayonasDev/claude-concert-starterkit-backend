package starters.springboot.claude.starterkit.queue.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import starters.springboot.claude.starterkit.common.response.ApiResponse;
import starters.springboot.claude.starterkit.queue.dto.QueueEnterResponse;
import starters.springboot.claude.starterkit.queue.dto.QueueStatusResponse;
import starters.springboot.claude.starterkit.queue.service.QueueTokenService;

@Tag(name = "Queue")
@RestController
@RequestMapping("/api/v1/queue")
@RequiredArgsConstructor
public class QueueController {

    private final QueueTokenService queueTokenService;

    @PostMapping("/{concertId}/enter")
    public ApiResponse<QueueEnterResponse> enter(@PathVariable Long concertId) {
        return ApiResponse.success(queueTokenService.enter(concertId));
    }

    @GetMapping("/{concertId}/status")
    public ApiResponse<QueueStatusResponse> status(@PathVariable Long concertId, @RequestParam String token) {
        return ApiResponse.success(queueTokenService.getStatus(concertId, token));
    }
}

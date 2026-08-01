package starters.springboot.claude.starterkit.concert.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import starters.springboot.claude.starterkit.common.response.ApiResponse;
import starters.springboot.claude.starterkit.concert.dto.ConcertCreateRequest;
import starters.springboot.claude.starterkit.concert.dto.ConcertDetailResponse;
import starters.springboot.claude.starterkit.concert.dto.SeatBulkCreateRequest;
import starters.springboot.claude.starterkit.concert.dto.SeatGradeCreateRequest;
import starters.springboot.claude.starterkit.concert.service.ConcertAdminService;

/**
 * ROLE_ADMIN 전용 (docs/api-spec.md 7장). 접근 제어는 SecurityConfig에서 "/api/v1/admin/**" 경로
 * 기준으로 일괄 적용한다.
 */
@Tag(name = "Admin")
@RestController
@RequestMapping("/api/v1/admin/concerts")
@RequiredArgsConstructor
public class AdminConcertController {

    private final ConcertAdminService concertAdminService;

    @PostMapping
    public ResponseEntity<ApiResponse<ConcertDetailResponse>> createConcert(
            @Valid @RequestBody ConcertCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(concertAdminService.createConcert(request)));
    }

    @PostMapping("/{concertId}/seat-grades")
    public ResponseEntity<ApiResponse<List<ConcertDetailResponse.SeatGradeResponse>>> createSeatGrades(
            @PathVariable Long concertId,
            @Valid @RequestBody List<@Valid SeatGradeCreateRequest> requests) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(concertAdminService.createSeatGrades(concertId, requests)));
    }

    @PostMapping("/{concertId}/seats/bulk")
    public ResponseEntity<ApiResponse<Integer>> createSeatsBulk(
            @PathVariable Long concertId,
            @Valid @RequestBody SeatBulkCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(concertAdminService.createSeatsBulk(concertId, request)));
    }
}

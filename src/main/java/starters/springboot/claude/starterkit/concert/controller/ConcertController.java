package starters.springboot.claude.starterkit.concert.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import starters.springboot.claude.starterkit.common.response.ApiResponse;
import starters.springboot.claude.starterkit.concert.domain.ConcertStatus;
import starters.springboot.claude.starterkit.concert.dto.ConcertDetailResponse;
import starters.springboot.claude.starterkit.concert.dto.ConcertSummaryResponse;
import starters.springboot.claude.starterkit.concert.dto.SeatResponse;
import starters.springboot.claude.starterkit.concert.service.ConcertQueryService;

@Tag(name = "Concert")
@RestController
@RequestMapping("/api/v1/concerts")
@RequiredArgsConstructor
public class ConcertController {

    private final ConcertQueryService concertQueryService;

    @GetMapping
    public ApiResponse<Page<ConcertSummaryResponse>> getConcerts(
            @RequestParam(required = false) ConcertStatus status,
            Pageable pageable) {
        return ApiResponse.success(concertQueryService.findConcerts(status, pageable));
    }

    @GetMapping("/{concertId}")
    public ApiResponse<ConcertDetailResponse> getConcert(@PathVariable Long concertId) {
        return ApiResponse.success(concertQueryService.getConcertDetail(concertId));
    }

    @GetMapping("/{concertId}/seats")
    public ApiResponse<List<SeatResponse>> getSeats(@PathVariable Long concertId) {
        return ApiResponse.success(concertQueryService.getSeatMap(concertId));
    }
}

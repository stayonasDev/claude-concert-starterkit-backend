package starters.springboot.claude.starterkit.concert.dto;

import java.time.LocalDateTime;
import starters.springboot.claude.starterkit.concert.domain.Concert;
import starters.springboot.claude.starterkit.concert.domain.ConcertStatus;

public record ConcertSummaryResponse(
        Long id,
        String title,
        String venue,
        LocalDateTime performanceAt,
        ConcertStatus status
) {

    public static ConcertSummaryResponse from(Concert concert) {
        return new ConcertSummaryResponse(
                concert.getId(),
                concert.getTitle(),
                concert.getVenue(),
                concert.getPerformanceAt(),
                concert.getStatus()
        );
    }
}

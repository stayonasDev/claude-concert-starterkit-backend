package starters.springboot.claude.starterkit.concert.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public record ConcertCreateRequest(
        @NotBlank String title,
        String description,
        @NotBlank String venue,
        @NotNull LocalDateTime performanceAt,
        @NotNull LocalDateTime bookingOpenAt,
        @NotNull LocalDateTime bookingCloseAt,
        String posterImageUrl
) {
}

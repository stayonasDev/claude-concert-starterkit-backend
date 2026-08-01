package starters.springboot.claude.starterkit.concert.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record SeatGradeCreateRequest(
        @NotBlank String gradeName,
        @NotNull @Positive BigDecimal price,
        @NotNull @Positive Integer totalCount
) {
}

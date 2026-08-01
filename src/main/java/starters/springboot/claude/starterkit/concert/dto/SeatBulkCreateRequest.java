package starters.springboot.claude.starterkit.concert.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record SeatBulkCreateRequest(
        @NotNull Long seatGradeId,
        @NotEmpty List<String> seatNumbers
) {
}

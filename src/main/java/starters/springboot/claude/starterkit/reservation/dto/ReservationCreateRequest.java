package starters.springboot.claude.starterkit.reservation.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record ReservationCreateRequest(
        @NotNull Long concertId,
        @NotEmpty List<Long> seatIds
) {
}

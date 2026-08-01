package starters.springboot.claude.starterkit.reservation.service;

import java.util.List;
import starters.springboot.claude.starterkit.common.lock.LockStrategyType;

public record ReserveSeatsCommand(
        Long userId,
        Long concertId,
        List<Long> seatIds,
        LockStrategyType lockStrategy
) {
}

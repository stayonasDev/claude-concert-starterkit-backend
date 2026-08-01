package starters.springboot.claude.starterkit.reservation.service;

import java.time.LocalDateTime;
import starters.springboot.claude.starterkit.common.lock.LockStrategyType;

public record SeatHoldResult(
        Long seatId,
        Long userId,
        LocalDateTime heldAt,
        LocalDateTime holdExpiresAt,
        LockStrategyType lockStrategy
) {
}

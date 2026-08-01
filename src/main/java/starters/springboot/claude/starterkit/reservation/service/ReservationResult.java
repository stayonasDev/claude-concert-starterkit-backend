package starters.springboot.claude.starterkit.reservation.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import starters.springboot.claude.starterkit.common.lock.LockStrategyType;
import starters.springboot.claude.starterkit.reservation.domain.Reservation;
import starters.springboot.claude.starterkit.reservation.domain.ReservationStatus;

public record ReservationResult(
        Long reservationId,
        ReservationStatus status,
        LockStrategyType lockStrategy,
        LocalDateTime holdExpiresAt,
        List<SeatLine> seats
) {

    static ReservationResult from(Reservation reservation) {
        List<SeatLine> seats = reservation.getReservationSeats().stream()
                .map(reservationSeat -> new SeatLine(reservationSeat.getSeatId(), reservationSeat.getPriceSnapshot()))
                .toList();

        return new ReservationResult(
                reservation.getId(),
                reservation.getStatus(),
                reservation.getLockStrategy(),
                reservation.getHoldExpiresAt(),
                seats
        );
    }

    public record SeatLine(Long seatId, BigDecimal priceSnapshot) {
    }
}

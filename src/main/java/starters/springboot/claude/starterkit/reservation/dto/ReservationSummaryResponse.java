package starters.springboot.claude.starterkit.reservation.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import starters.springboot.claude.starterkit.common.lock.LockStrategyType;
import starters.springboot.claude.starterkit.payment.domain.Payment;
import starters.springboot.claude.starterkit.payment.domain.PaymentStatus;
import starters.springboot.claude.starterkit.reservation.domain.Reservation;
import starters.springboot.claude.starterkit.reservation.domain.ReservationStatus;

/**
 * 본인 예약 목록 조회 응답 (docs/use-cases.md UC-10, docs/api-spec.md 4.3).
 */
public record ReservationSummaryResponse(
        Long reservationId,
        Long concertId,
        ReservationStatus status,
        LockStrategyType lockStrategy,
        LocalDateTime holdExpiresAt,
        List<SeatLine> seats,
        PaymentSummary payment
) {

    public static ReservationSummaryResponse of(Reservation reservation, Payment payment) {
        List<SeatLine> seats = reservation.getReservationSeats().stream()
                .map(reservationSeat -> new SeatLine(reservationSeat.getSeatId(), reservationSeat.getPriceSnapshot()))
                .toList();

        return new ReservationSummaryResponse(
                reservation.getId(),
                reservation.getConcertId(),
                reservation.getStatus(),
                reservation.getLockStrategy(),
                reservation.getHoldExpiresAt(),
                seats,
                payment != null ? PaymentSummary.from(payment) : null
        );
    }

    public record SeatLine(Long seatId, BigDecimal priceSnapshot) {
    }

    public record PaymentSummary(PaymentStatus status, BigDecimal amount, LocalDateTime paidAt) {
        static PaymentSummary from(Payment payment) {
            return new PaymentSummary(payment.getStatus(), payment.getAmount(), payment.getPaidAt());
        }
    }
}

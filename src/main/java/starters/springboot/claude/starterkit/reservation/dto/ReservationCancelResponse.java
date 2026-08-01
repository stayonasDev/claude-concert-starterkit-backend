package starters.springboot.claude.starterkit.reservation.dto;

import starters.springboot.claude.starterkit.reservation.domain.ReservationStatus;

public record ReservationCancelResponse(ReservationStatus status) {

    public static ReservationCancelResponse of(ReservationStatus status) {
        return new ReservationCancelResponse(status);
    }
}

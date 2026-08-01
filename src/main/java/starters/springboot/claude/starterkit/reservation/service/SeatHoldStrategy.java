package starters.springboot.claude.starterkit.reservation.service;

import java.time.LocalDateTime;

/**
 * 좌석 선점 동시성 제어 전략 (docs/architecture.md 3장).
 * 이 인터페이스는 의도적으로 "좌석 상태를 AVAILABLE → HELD로 배타적으로 전환"하는
 * 책임만 진다. Reservation/ReservationSeat 생성 등 예약 오케스트레이션은
 * 이 결과를 소비하는 상위 계층(향후 구현할 ReservationFacade)의 책임이다.
 */
public interface SeatHoldStrategy {

    SeatHoldResult hold(Long seatId, Long userId, LocalDateTime holdExpiresAt);
}

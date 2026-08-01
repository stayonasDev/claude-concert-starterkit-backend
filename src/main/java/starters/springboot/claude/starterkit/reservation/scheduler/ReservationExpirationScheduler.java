package starters.springboot.claude.starterkit.reservation.scheduler;

import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import starters.springboot.claude.starterkit.concert.domain.Seat;
import starters.springboot.claude.starterkit.concert.repository.SeatRepository;
import starters.springboot.claude.starterkit.reservation.domain.Reservation;
import starters.springboot.claude.starterkit.reservation.domain.ReservationStatus;
import starters.springboot.claude.starterkit.reservation.repository.ReservationRepository;

/**
 * 좌석 선점(HOLD) 후 일정 시간 내 결제가 완료되지 않은 예약을 자동 만료시킨다
 * (docs/requirements.md FR-13, docs/use-cases.md UC-09).
 *
 * 알려진 한계: 이 스케줄러가 "만료 대상"으로 조회한 시점과 실제 만료 처리(commit) 사이에
 * 결제가 동시에 완료(CONFIRMED로 전환)되는 극히 드문 경합이 이론적으로 가능하다. 완전한
 * 해결에는 Reservation에 낙관적 락(@Version)을 추가해야 하며, 이는 결제 도메인 구현과
 * 함께 재검토한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationExpirationScheduler {

    private final ReservationRepository reservationRepository;
    private final SeatRepository seatRepository;

    @Scheduled(fixedDelayString = "${app.reservation.expiration-check-interval-ms:30000}")
    @Transactional
    public void expireHoldingReservations() {
        LocalDateTime now = LocalDateTime.now();
        List<Reservation> expiredReservations =
                reservationRepository.findByStatusAndHoldExpiresAtBefore(ReservationStatus.HOLDING, now);

        for (Reservation reservation : expiredReservations) {
            reservation.expire();
            releaseSeats(reservation);
        }

        if (!expiredReservations.isEmpty()) {
            log.info("만료된 예약 {}건을 반환 처리했습니다.", expiredReservations.size());
        }
    }

    private void releaseSeats(Reservation reservation) {
        reservation.getReservationSeats().forEach(reservationSeat ->
                seatRepository.findById(reservationSeat.getSeatId()).ifPresent(Seat::release));
    }
}

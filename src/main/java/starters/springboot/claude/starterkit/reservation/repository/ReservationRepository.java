package starters.springboot.claude.starterkit.reservation.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import starters.springboot.claude.starterkit.reservation.domain.Reservation;
import starters.springboot.claude.starterkit.reservation.domain.ReservationStatus;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    List<Reservation> findByUserId(Long userId);

    // FR-06: 한 사용자가 한 콘서트에 대해 동시 보유 가능한 활성 예약은 1건
    boolean existsByUserIdAndConcertIdAndStatusIn(Long userId, Long concertId, Collection<ReservationStatus> statuses);

    // FR-13: ReservationExpirationScheduler의 만료 스캔 쿼리
    // (idx_reservations_status_hold_expires_at 인덱스 활용)
    List<Reservation> findByStatusAndHoldExpiresAtBefore(ReservationStatus status, LocalDateTime now);
}

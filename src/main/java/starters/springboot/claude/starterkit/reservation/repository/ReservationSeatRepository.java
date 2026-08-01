package starters.springboot.claude.starterkit.reservation.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import starters.springboot.claude.starterkit.reservation.domain.ReservationSeat;

public interface ReservationSeatRepository extends JpaRepository<ReservationSeat, Long> {

    List<ReservationSeat> findByReservationId(Long reservationId);
}

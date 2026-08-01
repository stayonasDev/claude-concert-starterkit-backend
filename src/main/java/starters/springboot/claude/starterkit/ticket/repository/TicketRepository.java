package starters.springboot.claude.starterkit.ticket.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import starters.springboot.claude.starterkit.ticket.domain.Ticket;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    Optional<Ticket> findByReservationSeatId(Long reservationSeatId);

    List<Ticket> findByReservationSeatIdIn(List<Long> reservationSeatIds);
}

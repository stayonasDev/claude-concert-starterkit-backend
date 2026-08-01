package starters.springboot.claude.starterkit.payment.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import starters.springboot.claude.starterkit.payment.domain.Payment;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByReservationId(Long reservationId);
}

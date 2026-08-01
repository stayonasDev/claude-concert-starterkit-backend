package starters.springboot.claude.starterkit.reservation.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import starters.springboot.claude.starterkit.payment.domain.Payment;
import starters.springboot.claude.starterkit.payment.repository.PaymentRepository;
import starters.springboot.claude.starterkit.reservation.dto.ReservationSummaryResponse;
import starters.springboot.claude.starterkit.reservation.repository.ReservationRepository;

/**
 * 본인 예약 목록 조회 (docs/use-cases.md UC-10, FR-17).
 */
@Service
@RequiredArgsConstructor
public class ReservationQueryService {

    private final ReservationRepository reservationRepository;
    private final PaymentRepository paymentRepository;

    @Transactional(readOnly = true)
    public List<ReservationSummaryResponse> findMyReservations(Long userId) {
        return reservationRepository.findByUserId(userId).stream()
                .map(reservation -> {
                    Payment payment = paymentRepository.findByReservationId(reservation.getId()).orElse(null);
                    return ReservationSummaryResponse.of(reservation, payment);
                })
                .toList();
    }
}

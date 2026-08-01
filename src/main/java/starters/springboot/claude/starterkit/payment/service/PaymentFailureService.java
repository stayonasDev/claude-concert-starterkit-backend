package starters.springboot.claude.starterkit.payment.service;

import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import starters.springboot.claude.starterkit.common.exception.BusinessException;
import starters.springboot.claude.starterkit.common.exception.ErrorCode;
import starters.springboot.claude.starterkit.concert.repository.SeatRepository;
import starters.springboot.claude.starterkit.payment.domain.Payment;
import starters.springboot.claude.starterkit.payment.domain.PaymentMethod;
import starters.springboot.claude.starterkit.payment.repository.PaymentRepository;
import starters.springboot.claude.starterkit.reservation.domain.Reservation;
import starters.springboot.claude.starterkit.reservation.repository.ReservationRepository;

/**
 * 결제 실패(FR-16) 시 좌석 반환/예약 취소를 별도 트랜잭션(REQUIRES_NEW)으로 즉시 커밋한다.
 *
 * {@link PaymentService#pay}는 전체가 하나의 @Transactional로 묶여 있는데, 결제 실패 처리
 * 직후 BusinessException(PAYMENT_FAILED)을 던져 컨트롤러까지 전파시킨다. 이 처리를 같은
 * 트랜잭션 안에서 하면, 예외 전파로 트랜잭션 전체가 롤백되면서 방금 반환한 좌석과 취소한
 * 예약까지 함께 사라져버린다(응답은 실패로 보이지만 DB는 여전히 HOLDING으로 남는 버그).
 *
 * 엔티티를 트랜잭션 경계 너머로 전달받아 merge하면, 원래 트랜잭션에서 이미 읽은 버전과
 * 여기서 다시 merge하는 시점의 실제 DB 버전이 어긋나 StaleObjectStateException이 날 수
 * 있다(이 메서드가 REQUIRES_NEW로 새 영속성 컨텍스트를 여는 것과 별개로, 원본 엔티티가
 * 이전 컨텍스트에 이미 관리되고 있던 상태이기 때문). 그래서 엔티티가 아니라 id/원시값만
 * 받아 이 트랜잭션 안에서 새로 조회 → 수정 → 저장까지 전부 이 컨텍스트 안에서 끝낸다.
 */
@Component
@RequiredArgsConstructor
public class PaymentFailureService {

    private final PaymentRepository paymentRepository;
    private final ReservationRepository reservationRepository;
    private final SeatRepository seatRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailureAndReleaseSeats(Long reservationId, BigDecimal amount, PaymentMethod method) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));

        Payment payment = Payment.builder()
                .reservationId(reservationId)
                .amount(amount)
                .method(method)
                .build();
        payment.markFailed();
        paymentRepository.save(payment);

        reservation.cancel();
        reservationRepository.save(reservation);

        reservation.getReservationSeats().forEach(reservationSeat ->
                seatRepository.findById(reservationSeat.getSeatId()).ifPresent(seat -> {
                    seat.release();
                    seatRepository.save(seat);
                }));
    }
}

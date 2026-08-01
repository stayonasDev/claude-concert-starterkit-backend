package starters.springboot.claude.starterkit.payment.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import starters.springboot.claude.starterkit.common.exception.BusinessException;
import starters.springboot.claude.starterkit.common.exception.ErrorCode;
import starters.springboot.claude.starterkit.concert.domain.Concert;
import starters.springboot.claude.starterkit.concert.domain.Seat;
import starters.springboot.claude.starterkit.concert.domain.SeatStatus;
import starters.springboot.claude.starterkit.concert.repository.ConcertRepository;
import starters.springboot.claude.starterkit.concert.repository.SeatRepository;
import starters.springboot.claude.starterkit.payment.domain.Payment;
import starters.springboot.claude.starterkit.payment.repository.PaymentRepository;
import starters.springboot.claude.starterkit.payment.service.MockPgClient.PgChargeResult;
import starters.springboot.claude.starterkit.reservation.domain.Reservation;
import starters.springboot.claude.starterkit.reservation.domain.ReservationSeat;
import starters.springboot.claude.starterkit.reservation.domain.ReservationStatus;
import starters.springboot.claude.starterkit.reservation.repository.ReservationRepository;
import starters.springboot.claude.starterkit.ticket.service.TicketService;

/**
 * 결제 확정/실패 오케스트레이션 (docs/use-cases.md UC-07, UC-08 / docs/requirements.md FR-15, FR-16).
 */
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final ReservationRepository reservationRepository;
    private final ConcertRepository concertRepository;
    private final SeatRepository seatRepository;
    private final PaymentRepository paymentRepository;
    private final MockPgClient mockPgClient;
    private final TicketService ticketService;

    @Transactional
    public PaymentResult pay(PaymentCommand command) {
        Reservation reservation = reservationRepository.findById(command.reservationId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));

        validatePayable(reservation);

        BigDecimal amount = totalAmount(reservation);
        Payment payment = Payment.builder()
                .reservationId(reservation.getId())
                .amount(amount)
                .method(command.method())
                .build();

        PgChargeResult chargeResult = mockPgClient.charge(amount, command.forceFail());

        if (!chargeResult.success()) {
            handlePaymentFailure(reservation, payment);
            throw new BusinessException(ErrorCode.PAYMENT_FAILED);
        }

        return handlePaymentSuccess(reservation, payment, chargeResult);
    }

    private void validatePayable(Reservation reservation) {
        if (paymentRepository.findByReservationId(reservation.getId()).isPresent()) {
            throw new BusinessException(ErrorCode.RESERVATION_ALREADY_PAID);
        }
        // HOLDING이 아니거나(이미 만료/취소/확정됨) HOLD 시간이 지난 예약은 결제할 수 없다.
        if (reservation.getStatus() != ReservationStatus.HOLDING
                || reservation.getHoldExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.RESERVATION_EXPIRED);
        }
    }

    private BigDecimal totalAmount(Reservation reservation) {
        return reservation.getReservationSeats().stream()
                .map(ReservationSeat::getPriceSnapshot)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // FR-16: 결제 실패 시 좌석을 즉시 반환해 재선점 가능하게 한다.
    private void handlePaymentFailure(Reservation reservation, Payment payment) {
        payment.markFailed();
        savePayment(payment);
        reservation.cancel();
        releaseSeats(reservation);
    }

    // FR-15: Payment/Reservation/Seat/Ticket 반영을 하나의 트랜잭션으로 처리한다.
    private PaymentResult handlePaymentSuccess(Reservation reservation, Payment payment, PgChargeResult chargeResult) {
        LocalDateTime now = LocalDateTime.now();
        payment.markPaid(chargeResult.transactionId(), now);
        Payment savedPayment = savePayment(payment);

        reservation.confirm(now);
        confirmSeats(reservation);
        ticketService.issueTicketsFor(reservation);
        markSoldOutIfNoSeatsRemain(reservation.getConcertId());

        return PaymentResult.from(savedPayment);
    }

    private Payment savePayment(Payment payment) {
        try {
            return paymentRepository.save(payment);
        } catch (DataIntegrityViolationException e) {
            // payments.reservation_id 유니크 제약 위반 — 동시에 두 번 결제 요청이 들어온
            // 드문 경합에 대한 안전망 (docs/architecture.md 6장 CAS 안전망과 동일한 사고방식).
            throw new BusinessException(ErrorCode.RESERVATION_ALREADY_PAID);
        }
    }

    private void confirmSeats(Reservation reservation) {
        reservation.getReservationSeats().forEach(reservationSeat ->
                seatRepository.findById(reservationSeat.getSeatId()).ifPresent(Seat::confirm));
    }

    private void releaseSeats(Reservation reservation) {
        reservation.getReservationSeats().forEach(reservationSeat ->
                seatRepository.findById(reservationSeat.getSeatId()).ifPresent(Seat::release));
    }

    // FR-20: 잔여 판매 가능 좌석이 없으면 콘서트를 SOLD_OUT으로 자동 전환한다.
    private void markSoldOutIfNoSeatsRemain(Long concertId) {
        boolean seatsRemain = seatRepository.existsByConcertIdAndStatusIn(
                concertId, List.of(SeatStatus.AVAILABLE, SeatStatus.HELD));
        if (!seatsRemain) {
            concertRepository.findById(concertId).ifPresent(Concert::markSoldOut);
        }
    }
}

package starters.springboot.claude.starterkit.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import starters.springboot.claude.starterkit.common.exception.BusinessException;
import starters.springboot.claude.starterkit.common.exception.ErrorCode;
import starters.springboot.claude.starterkit.common.lock.LockStrategyType;
import starters.springboot.claude.starterkit.concert.domain.Concert;
import starters.springboot.claude.starterkit.concert.domain.ConcertStatus;
import starters.springboot.claude.starterkit.concert.domain.Seat;
import starters.springboot.claude.starterkit.concert.domain.SeatGrade;
import starters.springboot.claude.starterkit.concert.domain.SeatStatus;
import starters.springboot.claude.starterkit.concert.repository.ConcertRepository;
import starters.springboot.claude.starterkit.concert.repository.SeatGradeRepository;
import starters.springboot.claude.starterkit.concert.repository.SeatRepository;
import starters.springboot.claude.starterkit.payment.domain.PaymentMethod;
import starters.springboot.claude.starterkit.payment.domain.PaymentStatus;
import starters.springboot.claude.starterkit.reservation.domain.Reservation;
import starters.springboot.claude.starterkit.reservation.domain.ReservationSeat;
import starters.springboot.claude.starterkit.reservation.domain.ReservationStatus;
import starters.springboot.claude.starterkit.reservation.repository.ReservationRepository;
import starters.springboot.claude.starterkit.reservation.service.ReservationFacade;
import starters.springboot.claude.starterkit.reservation.service.ReservationResult;
import starters.springboot.claude.starterkit.reservation.service.ReserveSeatsCommand;
import starters.springboot.claude.starterkit.support.ContainerTestSupport;
import starters.springboot.claude.starterkit.ticket.repository.TicketRepository;

/**
 * docs/use-cases.md UC-07, UC-08 대응.
 */
@Transactional
class PaymentServiceTest extends ContainerTestSupport {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private ReservationFacade reservationFacade;

    @Autowired
    private ConcertRepository concertRepository;

    @Autowired
    private SeatGradeRepository seatGradeRepository;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private TicketRepository ticketRepository;

    private Long concertId;
    private Long seatGradeId;
    private Long seatId;

    @BeforeEach
    void setUp() {
        LocalDateTime now = LocalDateTime.now();
        Concert concert = concertRepository.save(Concert.builder()
                .title("2026 World Tour")
                .venue("잠실 올림픽 주경기장")
                .performanceAt(now.plusDays(30))
                .bookingOpenAt(now.minusDays(1))
                .bookingCloseAt(now.plusDays(29))
                .build());
        this.concertId = concert.getId();

        SeatGrade seatGrade = seatGradeRepository.save(SeatGrade.builder()
                .concertId(concertId)
                .gradeName("VIP")
                .price(BigDecimal.valueOf(200000))
                .totalCount(10)
                .build());
        this.seatGradeId = seatGrade.getId();

        this.seatId = seatRepository.save(Seat.builder()
                .concertId(concertId)
                .seatGradeId(seatGradeId)
                .seatNumber("A-1")
                .build()).getId();
    }

    private Long createHoldingReservation(Long userId) {
        ReservationResult result = reservationFacade.reserve(new ReserveSeatsCommand(
                userId, concertId, List.of(seatId), LockStrategyType.REDIS));
        return result.reservationId();
    }

    @Test
    void 정상_결제시_예약_확정_좌석_확정_티켓_발급이_함께_이루어진다() {
        Long reservationId = createHoldingReservation(1L);

        PaymentResult result = paymentService.pay(
                new PaymentCommand(reservationId, PaymentMethod.MOCK, false));

        assertThat(result.status()).isEqualTo(PaymentStatus.PAID);
        assertThat(result.amount()).isEqualByComparingTo(BigDecimal.valueOf(200000));

        Reservation reservation = reservationRepository.findById(reservationId).orElseThrow();
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);

        Seat seat = seatRepository.findById(seatId).orElseThrow();
        assertThat(seat.getStatus()).isEqualTo(SeatStatus.RESERVED);

        List<ReservationSeat> reservationSeats = reservation.getReservationSeats();
        assertThat(reservationSeats).hasSize(1);
        assertThat(ticketRepository.findByReservationSeatId(reservationSeats.get(0).getId())).isPresent();
    }

    @Test
    void 결제_실패시_예약이_취소되고_좌석이_반환된다() {
        Long reservationId = createHoldingReservation(1L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> paymentService.pay(new PaymentCommand(reservationId, PaymentMethod.MOCK, true)));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_FAILED);

        Reservation reservation = reservationRepository.findById(reservationId).orElseThrow();
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);

        Seat seat = seatRepository.findById(seatId).orElseThrow();
        assertThat(seat.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
    }

    @Test
    void 존재하지_않는_예약이면_예외가_발생한다() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> paymentService.pay(new PaymentCommand(999999L, PaymentMethod.MOCK, false)));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.RESERVATION_NOT_FOUND);
    }

    @Test
    void 이미_결제된_예약을_재결제하면_예외가_발생한다() {
        Long reservationId = createHoldingReservation(1L);
        paymentService.pay(new PaymentCommand(reservationId, PaymentMethod.MOCK, false));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> paymentService.pay(new PaymentCommand(reservationId, PaymentMethod.MOCK, false)));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.RESERVATION_ALREADY_PAID);
    }

    @Test
    void HOLD_만료된_예약은_결제할_수_없다() {
        LocalDateTime now = LocalDateTime.now();
        Seat seat = seatRepository.findById(seatId).orElseThrow();
        seat.hold(1L, now.minusMinutes(10), now.minusMinutes(5));
        seatRepository.save(seat);

        Reservation reservation = Reservation.builder()
                .userId(1L)
                .concertId(concertId)
                .heldAt(now.minusMinutes(10))
                .holdExpiresAt(now.minusMinutes(5))
                .build();
        reservation.addReservationSeat(ReservationSeat.builder()
                .seatId(seatId)
                .priceSnapshot(BigDecimal.valueOf(200000))
                .build());
        Long reservationId = reservationRepository.save(reservation).getId();

        BusinessException exception = assertThrows(BusinessException.class,
                () -> paymentService.pay(new PaymentCommand(reservationId, PaymentMethod.MOCK, false)));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.RESERVATION_EXPIRED);
    }

    @Test
    void 모든_좌석이_판매되면_콘서트가_매진_처리된다() {
        Long secondSeatId = seatRepository.save(Seat.builder()
                .concertId(concertId)
                .seatGradeId(seatGradeId)
                .seatNumber("A-2")
                .build()).getId();

        Long firstReservationId = createHoldingReservation(1L);
        paymentService.pay(new PaymentCommand(firstReservationId, PaymentMethod.MOCK, false));

        assertThat(concertRepository.findById(concertId).orElseThrow().getStatus())
                .isEqualTo(ConcertStatus.UPCOMING);

        ReservationResult secondResult = reservationFacade.reserve(new ReserveSeatsCommand(
                2L, concertId, List.of(secondSeatId), LockStrategyType.REDIS));
        paymentService.pay(new PaymentCommand(secondResult.reservationId(), PaymentMethod.MOCK, false));

        assertThat(concertRepository.findById(concertId).orElseThrow().getStatus())
                .isEqualTo(ConcertStatus.SOLD_OUT);
    }
}

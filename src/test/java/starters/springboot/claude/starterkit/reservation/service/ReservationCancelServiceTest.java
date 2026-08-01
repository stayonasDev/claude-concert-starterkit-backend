package starters.springboot.claude.starterkit.reservation.service;

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
import starters.springboot.claude.starterkit.concert.domain.Seat;
import starters.springboot.claude.starterkit.concert.domain.SeatGrade;
import starters.springboot.claude.starterkit.concert.domain.SeatStatus;
import starters.springboot.claude.starterkit.concert.repository.ConcertRepository;
import starters.springboot.claude.starterkit.concert.repository.SeatGradeRepository;
import starters.springboot.claude.starterkit.concert.repository.SeatRepository;
import starters.springboot.claude.starterkit.payment.service.PaymentCommand;
import starters.springboot.claude.starterkit.payment.service.PaymentService;
import starters.springboot.claude.starterkit.payment.domain.PaymentMethod;
import starters.springboot.claude.starterkit.reservation.domain.Reservation;
import starters.springboot.claude.starterkit.reservation.domain.ReservationStatus;
import starters.springboot.claude.starterkit.reservation.repository.ReservationRepository;
import starters.springboot.claude.starterkit.support.ContainerTestSupport;
import starters.springboot.claude.starterkit.ticket.domain.TicketStatus;
import starters.springboot.claude.starterkit.ticket.repository.TicketRepository;

/**
 * docs/use-cases.md UC-11 대응.
 */
@Transactional
class ReservationCancelServiceTest extends ContainerTestSupport {

    @Autowired
    private ReservationCancelService reservationCancelService;

    @Autowired
    private ReservationFacade reservationFacade;

    @Autowired
    private PaymentService paymentService;

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
    private Long seatId;

    private Long createConcert(LocalDateTime performanceAt) {
        LocalDateTime now = LocalDateTime.now();
        Concert concert = concertRepository.save(Concert.builder()
                .title("2026 World Tour")
                .venue("잠실 올림픽 주경기장")
                .performanceAt(performanceAt)
                .bookingOpenAt(now.minusDays(1))
                .bookingCloseAt(now.plusDays(29))
                .build());
        return concert.getId();
    }

    private Long createSeat(Long concertId) {
        SeatGrade seatGrade = seatGradeRepository.save(SeatGrade.builder()
                .concertId(concertId)
                .gradeName("VIP")
                .price(BigDecimal.valueOf(200000))
                .totalCount(10)
                .build());
        return seatRepository.save(Seat.builder()
                .concertId(concertId)
                .seatGradeId(seatGrade.getId())
                .seatNumber("A-1")
                .build()).getId();
    }

    private Long createConfirmedReservation(Long userId) {
        Long reservationId = reservationFacade.reserve(new ReserveSeatsCommand(
                userId, concertId, List.of(seatId), LockStrategyType.REDIS)).reservationId();
        paymentService.pay(new PaymentCommand(reservationId, PaymentMethod.MOCK, false));
        return reservationId;
    }

    @BeforeEach
    void setUp() {
        this.concertId = createConcert(LocalDateTime.now().plusDays(30));
        this.seatId = createSeat(concertId);
    }

    @Test
    void 확정된_예약을_취소하면_좌석과_티켓이_반환된다() {
        Long reservationId = createConfirmedReservation(1L);

        ReservationStatus status = reservationCancelService.cancel(reservationId, 1L);

        assertThat(status).isEqualTo(ReservationStatus.CANCELLED);

        Reservation reservation = reservationRepository.findById(reservationId).orElseThrow();
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);

        Seat seat = seatRepository.findById(seatId).orElseThrow();
        assertThat(seat.getStatus()).isEqualTo(SeatStatus.AVAILABLE);

        Long reservationSeatId = reservation.getReservationSeats().get(0).getId();
        assertThat(ticketRepository.findByReservationSeatId(reservationSeatId).orElseThrow().getStatus())
                .isEqualTo(TicketStatus.CANCELLED);
    }

    @Test
    void 존재하지_않는_예약이면_예외가_발생한다() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> reservationCancelService.cancel(999999L, 1L));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.RESERVATION_NOT_FOUND);
    }

    @Test
    void 본인_소유가_아닌_예약을_취소하면_예외가_발생한다() {
        Long reservationId = createConfirmedReservation(1L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> reservationCancelService.cancel(reservationId, 2L));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_RESERVATION_OWNER);
    }

    @Test
    void 확정되지_않은_예약을_취소하면_예외가_발생한다() {
        Long reservationId = reservationFacade.reserve(new ReserveSeatsCommand(
                1L, concertId, List.of(seatId), LockStrategyType.REDIS)).reservationId();

        BusinessException exception = assertThrows(BusinessException.class,
                () -> reservationCancelService.cancel(reservationId, 1L));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.RESERVATION_NOT_FOUND);
    }

    @Test
    void 공연_D_1_이내에는_취소할_수_없다() {
        Long soonConcertId = createConcert(LocalDateTime.now().plusHours(12));
        Long soonSeatId = createSeat(soonConcertId);
        Long reservationId = reservationFacade.reserve(new ReserveSeatsCommand(
                1L, soonConcertId, List.of(soonSeatId), LockStrategyType.REDIS)).reservationId();
        paymentService.pay(new PaymentCommand(reservationId, PaymentMethod.MOCK, false));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> reservationCancelService.cancel(reservationId, 1L));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CANCELLATION_DEADLINE_PASSED);
    }
}

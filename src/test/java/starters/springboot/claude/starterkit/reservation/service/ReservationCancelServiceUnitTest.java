package starters.springboot.claude.starterkit.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import starters.springboot.claude.starterkit.common.exception.BusinessException;
import starters.springboot.claude.starterkit.common.exception.ErrorCode;
import starters.springboot.claude.starterkit.concert.domain.Concert;
import starters.springboot.claude.starterkit.concert.domain.Seat;
import starters.springboot.claude.starterkit.concert.domain.SeatStatus;
import starters.springboot.claude.starterkit.concert.repository.ConcertRepository;
import starters.springboot.claude.starterkit.concert.repository.SeatRepository;
import starters.springboot.claude.starterkit.reservation.domain.Reservation;
import starters.springboot.claude.starterkit.reservation.domain.ReservationSeat;
import starters.springboot.claude.starterkit.reservation.domain.ReservationStatus;
import starters.springboot.claude.starterkit.reservation.repository.ReservationRepository;
import starters.springboot.claude.starterkit.ticket.domain.Ticket;
import starters.springboot.claude.starterkit.ticket.domain.TicketStatus;
import starters.springboot.claude.starterkit.ticket.repository.TicketRepository;

/**
 * Repository를 Mockito로 mock하는 순수 단위 테스트.
 * 컨테이너 기동이 필요 없어 {@link ReservationCancelServiceTest}(TestContainers 통합 테스트)보다 빠르게 실행된다.
 */
@ExtendWith(MockitoExtension.class)
class ReservationCancelServiceUnitTest {

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private ConcertRepository concertRepository;

    @Mock
    private SeatRepository seatRepository;

    @Mock
    private TicketRepository ticketRepository;

    @InjectMocks
    private ReservationCancelService reservationCancelService;

    private Reservation confirmedReservation(Long userId, Long seatId, Long reservationSeatId) {
        Reservation reservation = Reservation.builder()
                .userId(userId)
                .concertId(10L)
                .heldAt(LocalDateTime.now().minusMinutes(10))
                .holdExpiresAt(LocalDateTime.now().minusMinutes(5))
                .build();
        ReservationSeat reservationSeat = ReservationSeat.builder()
                .seatId(seatId)
                .priceSnapshot(BigDecimal.valueOf(200000))
                .build();
        ReflectionTestUtils.setField(reservationSeat, "id", reservationSeatId);
        reservation.addReservationSeat(reservationSeat);
        reservation.confirm(LocalDateTime.now().minusMinutes(4));
        return reservation;
    }

    private Concert concertWithPerformanceAt(LocalDateTime performanceAt) {
        return Concert.builder()
                .title("2026 World Tour")
                .venue("잠실 올림픽 주경기장")
                .performanceAt(performanceAt)
                .bookingOpenAt(LocalDateTime.now().minusDays(30))
                .bookingCloseAt(LocalDateTime.now().plusDays(30))
                .build();
    }

    @Test
    void 존재하지_않는_예약이면_예외가_발생한다() {
        given(reservationRepository.findById(1L)).willReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> reservationCancelService.cancel(1L, 1L));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.RESERVATION_NOT_FOUND);
    }

    @Test
    void 본인_소유가_아닌_예약을_취소하면_예외가_발생한다() {
        Reservation reservation = confirmedReservation(1L, 100L, 555L);
        given(reservationRepository.findById(1L)).willReturn(Optional.of(reservation));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> reservationCancelService.cancel(1L, 2L));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_RESERVATION_OWNER);
    }

    @Test
    void CONFIRMED_상태가_아니면_예외가_발생한다() {
        Reservation holding = Reservation.builder()
                .userId(1L)
                .concertId(10L)
                .heldAt(LocalDateTime.now())
                .holdExpiresAt(LocalDateTime.now().plusMinutes(5))
                .build();
        given(reservationRepository.findById(1L)).willReturn(Optional.of(holding));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> reservationCancelService.cancel(1L, 1L));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.RESERVATION_NOT_FOUND);
    }

    @Test
    void 공연_D_1_이내면_예외가_발생한다() {
        Reservation reservation = confirmedReservation(1L, 100L, 555L);
        given(reservationRepository.findById(1L)).willReturn(Optional.of(reservation));
        given(concertRepository.findById(10L)).willReturn(Optional.of(concertWithPerformanceAt(LocalDateTime.now().plusHours(12))));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> reservationCancelService.cancel(1L, 1L));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CANCELLATION_DEADLINE_PASSED);
    }

    @Test
    void 정상_취소시_좌석은_반환되고_티켓은_취소된다() {
        Reservation reservation = confirmedReservation(1L, 100L, 555L);
        Seat seat = Seat.builder().concertId(10L).seatGradeId(1L).seatNumber("A-1").build();
        seat.hold(1L, LocalDateTime.now().minusMinutes(10), LocalDateTime.now().minusMinutes(5));
        seat.confirm();
        Ticket ticket = Ticket.builder()
                .reservationSeatId(555L)
                .ticketNumber("ticket-number")
                .qrCodeValue("qr")
                .issuedAt(LocalDateTime.now().minusMinutes(4))
                .build();

        given(reservationRepository.findById(1L)).willReturn(Optional.of(reservation));
        given(concertRepository.findById(10L)).willReturn(Optional.of(concertWithPerformanceAt(LocalDateTime.now().plusDays(30))));
        given(seatRepository.findById(100L)).willReturn(Optional.of(seat));
        given(ticketRepository.findByReservationSeatId(555L)).willReturn(Optional.of(ticket));

        ReservationStatus status = reservationCancelService.cancel(1L, 1L);

        assertThat(status).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(seat.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.CANCELLED);
    }
}

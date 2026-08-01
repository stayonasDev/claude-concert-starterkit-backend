package starters.springboot.claude.starterkit.reservation.service;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import starters.springboot.claude.starterkit.common.exception.BusinessException;
import starters.springboot.claude.starterkit.common.exception.ErrorCode;
import starters.springboot.claude.starterkit.concert.domain.Concert;
import starters.springboot.claude.starterkit.concert.repository.ConcertRepository;
import starters.springboot.claude.starterkit.concert.repository.SeatRepository;
import starters.springboot.claude.starterkit.reservation.domain.Reservation;
import starters.springboot.claude.starterkit.reservation.domain.ReservationStatus;
import starters.springboot.claude.starterkit.reservation.repository.ReservationRepository;
import starters.springboot.claude.starterkit.ticket.domain.Ticket;
import starters.springboot.claude.starterkit.ticket.repository.TicketRepository;

/**
 * 예약 취소 (docs/use-cases.md UC-11, docs/requirements.md FR-18).
 */
@Service
@RequiredArgsConstructor
public class ReservationCancelService {

    private final ReservationRepository reservationRepository;
    private final ConcertRepository concertRepository;
    private final SeatRepository seatRepository;
    private final TicketRepository ticketRepository;

    @Transactional
    public ReservationStatus cancel(Long reservationId, Long userId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));

        if (!reservation.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NOT_RESERVATION_OWNER);
        }

        // docs/api-spec.md 4.4절은 404/403/422만 정의한다. CONFIRMED가 아닌 예약(이미 취소·
        // 만료되었거나 아직 결제 전인 예약)에 대한 취소 시도는 취소 가능한 대상이 존재하지
        // 않는 것과 동일하게 취급해 404로 응답한다.
        if (reservation.getStatus() != ReservationStatus.CONFIRMED) {
            throw new BusinessException(ErrorCode.RESERVATION_NOT_FOUND);
        }

        Concert concert = concertRepository.findById(reservation.getConcertId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CONCERT_NOT_FOUND));

        // FR-18: 공연 D-1 이전까지만 취소 가능
        LocalDateTime cancellationDeadline = concert.getPerformanceAt().minusDays(1);
        if (LocalDateTime.now().isAfter(cancellationDeadline)) {
            throw new BusinessException(ErrorCode.CANCELLATION_DEADLINE_PASSED);
        }

        reservation.cancel();
        reservation.getReservationSeats().forEach(reservationSeat -> {
            seatRepository.findById(reservationSeat.getSeatId()).ifPresent(seat -> seat.release());
            ticketRepository.findByReservationSeatId(reservationSeat.getId()).ifPresent(Ticket::cancel);
        });

        return reservation.getStatus();
    }
}

package starters.springboot.claude.starterkit.ticket.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import starters.springboot.claude.starterkit.reservation.domain.Reservation;
import starters.springboot.claude.starterkit.reservation.domain.ReservationSeat;
import starters.springboot.claude.starterkit.reservation.repository.ReservationRepository;
import starters.springboot.claude.starterkit.ticket.domain.Ticket;
import starters.springboot.claude.starterkit.ticket.dto.TicketSummaryResponse;
import starters.springboot.claude.starterkit.ticket.repository.TicketRepository;

@Service
@RequiredArgsConstructor
public class TicketService {

    private final TicketRepository ticketRepository;
    private final ReservationRepository reservationRepository;

    /**
     * 결제 확정 트랜잭션(PaymentService.pay) 내부에서 호출된다 (FR-15: 예약 확정·좌석 확정·
     * 티켓 발급이 하나의 트랜잭션으로 처리되어야 함). 좌석 라인아이템마다 별도 QR 티켓을
     * 발급한다 (docs/architecture.md A-1 설계 근거).
     */
    @Transactional
    public void issueTicketsFor(Reservation reservation) {
        LocalDateTime now = LocalDateTime.now();
        for (ReservationSeat reservationSeat : reservation.getReservationSeats()) {
            Ticket ticket = Ticket.builder()
                    .reservationSeatId(reservationSeat.getId())
                    .ticketNumber(UUID.randomUUID().toString())
                    .qrCodeValue(UUID.randomUUID().toString())
                    .issuedAt(now)
                    .build();
            ticketRepository.save(ticket);
        }
    }

    // UC-10: 본인 예약/티켓 조회. seatNumber/concertTitle 등 표시용 부가 정보 조인은
    // 이후 조회 성능/캐시 전략과 함께 별도로 보강한다 (지금은 티켓 자체 정보만 반환).
    @Transactional(readOnly = true)
    public List<TicketSummaryResponse> findMyTickets(Long userId) {
        List<Long> reservationSeatIds = reservationRepository.findByUserId(userId).stream()
                .flatMap(reservation -> reservation.getReservationSeats().stream())
                .map(ReservationSeat::getId)
                .toList();

        return ticketRepository.findByReservationSeatIdIn(reservationSeatIds).stream()
                .map(TicketSummaryResponse::from)
                .toList();
    }
}

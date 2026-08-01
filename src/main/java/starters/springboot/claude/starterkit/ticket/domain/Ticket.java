package starters.springboot.claude.starterkit.ticket.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "tickets")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // reservation_seats : tickets = 1:1 (uk_tickets_reservation_seat)
    @Column(name = "reservation_seat_id", nullable = false, unique = true)
    private Long reservationSeatId;

    @Column(name = "ticket_number", nullable = false, unique = true, length = 36)
    private String ticketNumber;

    @Column(name = "qr_code_value", length = 500)
    private String qrCodeValue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TicketStatus status;

    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;

    @Builder
    private Ticket(Long reservationSeatId, String ticketNumber, String qrCodeValue, LocalDateTime issuedAt) {
        this.reservationSeatId = reservationSeatId;
        this.ticketNumber = ticketNumber;
        this.qrCodeValue = qrCodeValue;
        this.issuedAt = issuedAt;
        this.status = TicketStatus.ISSUED;
    }

    public void use() {
        if (this.status != TicketStatus.ISSUED) {
            throw new IllegalStateException("사용 처리할 수 없는 티켓 상태입니다. status=" + status);
        }
        this.status = TicketStatus.USED;
    }

    public void cancel() {
        this.status = TicketStatus.CANCELLED;
    }
}

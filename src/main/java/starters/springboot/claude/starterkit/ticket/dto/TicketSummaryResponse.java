package starters.springboot.claude.starterkit.ticket.dto;

import java.time.LocalDateTime;
import starters.springboot.claude.starterkit.ticket.domain.Ticket;
import starters.springboot.claude.starterkit.ticket.domain.TicketStatus;

public record TicketSummaryResponse(
        Long ticketId,
        String ticketNumber,
        String qrCodeValue,
        TicketStatus status,
        LocalDateTime issuedAt
) {

    public static TicketSummaryResponse from(Ticket ticket) {
        return new TicketSummaryResponse(
                ticket.getId(),
                ticket.getTicketNumber(),
                ticket.getQrCodeValue(),
                ticket.getStatus(),
                ticket.getIssuedAt()
        );
    }
}

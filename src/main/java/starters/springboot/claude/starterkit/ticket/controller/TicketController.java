package starters.springboot.claude.starterkit.ticket.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import starters.springboot.claude.starterkit.common.response.ApiResponse;
import starters.springboot.claude.starterkit.ticket.dto.TicketSummaryResponse;
import starters.springboot.claude.starterkit.ticket.service.TicketService;
import starters.springboot.claude.starterkit.user.security.AuthenticatedUser;

@Tag(name = "Ticket")
@RestController
@RequestMapping("/api/v1/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;

    @GetMapping
    public ApiResponse<List<TicketSummaryResponse>> getMyTickets(Authentication authentication) {
        Long userId = AuthenticatedUser.from(authentication).id();
        return ApiResponse.success(ticketService.findMyTickets(userId));
    }
}

package com.dataart.tickets.ticket;

import com.dataart.tickets.ticket.dto.StateChangeRequest;
import com.dataart.tickets.ticket.dto.TicketRequest;
import com.dataart.tickets.ticket.dto.TicketResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Ticket CRUD (architecture.md §8, FR-K1..K4,K6,K8). Authenticated. List is scoped by the
 * {@code teamId} query parameter (FR-B1); create/update carry the ticket fields in the body.
 * The creator is resolved from the security context, never the request body (FR-K1).
 */
@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private final TicketService tickets;

    public TicketController(TicketService tickets) {
        this.tickets = tickets;
    }

    @GetMapping
    public List<TicketResponse> list(@RequestParam("teamId") UUID teamId,
                                     @RequestParam(value = "type", required = false) TicketType type,
                                     @RequestParam(value = "epicId", required = false) UUID epicId,
                                     @RequestParam(value = "q", required = false) String q) {
        return tickets.search(teamId, type, epicId, q).stream().map(TicketResponse::from).toList();
    }

    @GetMapping("/{id}")
    public TicketResponse get(@PathVariable UUID id) {
        return TicketResponse.from(tickets.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TicketResponse create(@Valid @RequestBody TicketRequest request, Authentication authentication) {
        Ticket ticket = tickets.create(request.teamId(), request.epicId(), request.type(),
                request.state(), request.title(), request.body(), authentication.getName());
        return TicketResponse.from(ticket);
    }

    @PutMapping("/{id}")
    public TicketResponse update(@PathVariable UUID id, @Valid @RequestBody TicketRequest request) {
        Ticket ticket = tickets.update(id, request.teamId(), request.epicId(), request.type(),
                request.state(), request.title(), request.body());
        return TicketResponse.from(ticket);
    }

    @PatchMapping("/{id}/state")
    public TicketResponse changeState(@PathVariable UUID id, @Valid @RequestBody StateChangeRequest request) {
        return TicketResponse.from(tickets.changeState(id, request.state()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        tickets.delete(id);
        return ResponseEntity.noContent().build();
    }
}

package com.example.telecom.controller;

import com.example.telecom.model.SupportTicket;
import com.example.telecom.service.TicketService;
import com.example.telecom.service.TicketService.TicketDetails;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tickets")
public class SupportTicketController {

    private static final int MAX_PAGE_SIZE = 100;
    private static final String PRIORITY = "priority";
    private static final String STATUS = "status";

    private final TicketService service;

    public SupportTicketController(TicketService service) {
        this.service = service;
    }

    @GetMapping
    public PagedResponse getTickets(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size, @RequestParam(required = false) String priority,
            @RequestParam(required = false) String status, @RequestParam(required = false) Long customerId) {
        Page<SupportTicket> result = service.search(Inputs.enumValue(SupportTicket.Priority.class, priority, PRIORITY),
                Inputs.enumValue(SupportTicket.Status.class, status, STATUS), customerId,
                PageRequest.of(Math.max(page, 0), Math.max(1, Math.min(size, MAX_PAGE_SIZE))));
        return new PagedResponse(result.getContent().stream().map(TicketResponse::from).toList(), result.getNumber(),
                result.getTotalPages(), result.getTotalElements(), result.hasNext());
    }

    @GetMapping("/{id}")
    public TicketResponse getTicket(@PathVariable Long id) {
        return TicketResponse.from(service.get(id));
    }

    @PostMapping
    public TicketResponse createTicket(@Valid @RequestBody TicketRequest request) {
        return TicketResponse.from(service.create(request.toDetails()));
    }

    @PutMapping("/{id}")
    public TicketResponse updateTicket(@PathVariable Long id, @Valid @RequestBody TicketRequest request) {
        return TicketResponse.from(service.update(id, request.toDetails()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTicket(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    public record TicketRequest(@Positive Long customerId, @NotBlank String subject, String description,
            @NotBlank String priority, String status) {
        TicketDetails toDetails() {
            return new TicketDetails(customerId, subject, description,
                    Inputs.enumValue(SupportTicket.Priority.class, priority, PRIORITY),
                    Inputs.enumValue(SupportTicket.Status.class, status, STATUS));
        }
    }

    public record PagedResponse(List<TicketResponse> content, int page, int totalPages, long totalElements,
            boolean hasNext) {
    }

    public record TicketResponse(Long id, Long customer_id, String customer_name, String subject, String description,
            String priority, String status, String created_at, String resolved_at) {
        public static TicketResponse from(SupportTicket ticket) {
            return new TicketResponse(ticket.getId(), ticket.getCustomer().getId(),
                    ticket.getCustomer().getFirstName() + " " + ticket.getCustomer().getLastName(), ticket.getSubject(),
                    ticket.getDescription(), ticket.getPriority().name(), ticket.getStatus().name(),
                    ticket.getCreatedAt() != null ? ticket.getCreatedAt().toString() : null,
                    ticket.getResolvedAt() != null ? ticket.getResolvedAt().toString() : null);
        }
    }
}

package com.example.telecom.controller;

import com.example.telecom.model.SupportTicket;
import com.example.telecom.repository.CustomerRepository;
import com.example.telecom.repository.SupportTicketRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/tickets")
public class SupportTicketController {

    private static final Logger LOG = LoggerFactory.getLogger(SupportTicketController.class);

    private final SupportTicketRepository repository;
    private final CustomerRepository customerRepository;

    public SupportTicketController(SupportTicketRepository repository, CustomerRepository customerRepository) {
        this.repository = repository;
        this.customerRepository = customerRepository;
    }

    @GetMapping
    public PagedResponse getTickets(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size, @RequestParam(required = false) String priority,
            @RequestParam(required = false) String status, @RequestParam(required = false) Long customerId) {
        LOG.info("Fetching tickets");
        SupportTicket.Priority priorityEnum = Inputs.enumValue(SupportTicket.Priority.class, priority, "priority");
        SupportTicket.Status statusEnum = Inputs.enumValue(SupportTicket.Status.class, status, "status");

        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), Math.max(1, Math.min(size, 100)));
        Page<SupportTicket> result;

        boolean hasFilters = priorityEnum != null || statusEnum != null || customerId != null;
        if (hasFilters) {
            result = repository.findFiltered(priorityEnum, statusEnum, customerId, pageRequest);
        } else {
            result = repository.findAllByOrderByCreatedAtDescIdDesc(pageRequest);
        }

        LOG.debug("Found {} tickets (page {} of {})", result.getNumberOfElements(), result.getNumber(),
                result.getTotalPages());
        return new PagedResponse(result.getContent().stream().map(TicketResponse::from).toList(), result.getNumber(),
                result.getTotalPages(), result.getTotalElements(), result.hasNext());
    }

    @GetMapping("/{id}")
    public ResponseEntity<TicketResponse> getTicket(@PathVariable Long id) {
        LOG.info("Fetching ticket with id={}", id);
        return repository.findById(id).map(ticket -> {
            LOG.debug("Found ticket: id={}", ticket.getId());
            return ResponseEntity.ok(TicketResponse.from(ticket));
        }).orElseGet(() -> {
            LOG.warn("Ticket not found: id={}", id);
            return ResponseEntity.notFound().build();
        });
    }

    @PostMapping
    public ResponseEntity<TicketResponse> createTicket(@Valid @RequestBody TicketRequest request) {
        LOG.info("Creating ticket");
        if (request.customerId() == null) {
            throw new FieldProblem("customerId", "customerId is required");
        }
        Inputs.enumValue(SupportTicket.Status.class, request.status(), "status");
        return customerRepository.findById(request.customerId()).map(customer -> {
            SupportTicket ticket = new SupportTicket(customer, request.subject(),
                    request.description() == null ? "" : request.description(),
                    Inputs.enumValue(SupportTicket.Priority.class, request.priority(), "priority"));
            SupportTicket saved = repository.save(ticket);
            LOG.info("Ticket created: id={}", saved.getId());
            return ResponseEntity.ok(TicketResponse.from(saved));
        }).orElseGet(() -> {
            LOG.warn("Customer not found for ticket creation: customerId={}", request.customerId());
            throw new FieldProblem("customerId", "Unknown customerId");
        });
    }

    @PutMapping("/{id}")
    public ResponseEntity<TicketResponse> updateTicket(@PathVariable Long id,
            @Valid @RequestBody TicketRequest request) {
        LOG.info("Updating ticket: id={}", id);
        return repository.findById(id).map(ticket -> {
            ticket.setSubject(request.subject());
            ticket.setDescription(request.description() == null ? "" : request.description());
            if (request.priority() != null) {
                ticket.setPriority(Inputs.enumValue(SupportTicket.Priority.class, request.priority(), "priority"));
            }
            if (request.status() != null) {
                applyStatus(ticket, Inputs.enumValue(SupportTicket.Status.class, request.status(), "status"));
            }
            SupportTicket saved = repository.save(ticket);
            LOG.info("Ticket updated: id={}, status={}", saved.getId(), saved.getStatus());
            return ResponseEntity.ok(TicketResponse.from(saved));
        }).orElseGet(() -> {
            LOG.warn("Ticket not found for update: id={}", id);
            return ResponseEntity.notFound().build();
        });
    }

    private static void applyStatus(SupportTicket ticket, SupportTicket.Status status) {
        ticket.setStatus(status);
        if (status == SupportTicket.Status.OPEN || status == SupportTicket.Status.IN_PROGRESS) {
            ticket.setResolvedAt(null);
        } else if (ticket.getResolvedAt() == null) {
            ticket.setResolvedAt(Instant.now());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTicket(@PathVariable Long id) {
        LOG.info("Deleting ticket: id={}", id);
        if (!repository.existsById(id)) {
            LOG.warn("Ticket not found for deletion: id={}", id);
            return ResponseEntity.notFound().build();
        }
        repository.deleteById(id);
        LOG.info("Ticket deleted: id={}", id);
        return ResponseEntity.noContent().build();
    }

    public record TicketRequest(@Positive Long customerId, @NotBlank String subject, String description,
            @NotBlank String priority, String status) {
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

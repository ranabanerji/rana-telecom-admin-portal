package com.example.telecom.service;

import com.example.telecom.model.Customer;
import com.example.telecom.model.SupportTicket;
import com.example.telecom.repository.CustomerRepository;
import com.example.telecom.repository.SupportTicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * Support ticket rules:
 * <ul>
 * <li>a ticket belongs to an existing customer and starts OPEN;</li>
 * <li>a missing description is stored as empty text;</li>
 * <li>status changes follow {@link #changeStatus}.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class TicketService {

    private static final Logger LOG = LoggerFactory.getLogger(TicketService.class);
    private static final String CUSTOMER_ID = "customerId";

    private final SupportTicketRepository tickets;
    private final CustomerRepository customers;
    private final Clock clock;

    public TicketService(SupportTicketRepository tickets, CustomerRepository customers, Clock clock) {
        this.tickets = tickets;
        this.customers = customers;
        this.clock = clock;
    }

    /** Newest tickets first, optionally narrowed by any of the filters. */
    public Page<SupportTicket> search(SupportTicket.Priority priority, SupportTicket.Status status, Long customerId,
            Pageable pageable) {
        if (priority == null && status == null && customerId == null) {
            return tickets.findAllByOrderByCreatedAtDescIdDesc(pageable);
        }
        return tickets.findFiltered(priority, status, customerId, pageable);
    }

    public SupportTicket get(Long id) {
        return tickets.findById(id).orElseThrow(() -> new NotFoundException("Ticket", id));
    }

    @Transactional
    public SupportTicket create(TicketDetails details) {
        if (details.customerId() == null) {
            throw new InvalidInputException(CUSTOMER_ID, "customerId is required");
        }
        Customer customer = customers.findById(details.customerId())
                .orElseThrow(() -> new InvalidInputException(CUSTOMER_ID, "Unknown customerId"));
        SupportTicket saved = tickets
                .save(new SupportTicket(customer, details.subject(), descriptionOf(details), details.priority()));
        LOG.info("Ticket created: id={}", saved.getId());
        return saved;
    }

    @Transactional
    public SupportTicket update(Long id, TicketDetails details) {
        SupportTicket ticket = get(id);
        ticket.setSubject(details.subject());
        ticket.setDescription(descriptionOf(details));
        if (details.priority() != null) {
            ticket.setPriority(details.priority());
        }
        if (details.status() != null) {
            changeStatus(ticket, details.status());
        }
        SupportTicket saved = tickets.save(ticket);
        LOG.info("Ticket updated: id={}, status={}", saved.getId(), saved.getStatus());
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        if (!tickets.existsById(id)) {
            throw new NotFoundException("Ticket", id);
        }
        tickets.deleteById(id);
        LOG.info("Ticket deleted: id={}", id);
    }

    /**
     * The ticket status state machine. Agents may move a ticket between any two
     * statuses; the move decides the resolution time:
     * <ul>
     * <li>to OPEN or IN_PROGRESS (unresolved): {@code resolvedAt} is cleared, so a
     * reopened ticket no longer looks resolved;</li>
     * <li>from an unresolved status to RESOLVED or CLOSED: {@code resolvedAt} is
     * set to now;</li>
     * <li>between RESOLVED and CLOSED, or to the same status: {@code resolvedAt}
     * keeps the original resolution time.</li>
     * </ul>
     */
    void changeStatus(SupportTicket ticket, SupportTicket.Status target) {
        if (target.isResolved()) {
            if (!ticket.getStatus().isResolved() || ticket.getResolvedAt() == null) {
                ticket.setResolvedAt(clock.instant());
            }
        } else {
            ticket.setResolvedAt(null);
        }
        ticket.setStatus(target);
    }

    private static String descriptionOf(TicketDetails details) {
        return details.description() == null ? "" : details.description();
    }

    /**
     * Ticket fields from a create or update request. {@code customerId} is used
     * only on create; {@code status} is ignored on create and, like
     * {@code priority}, left unchanged on update when null.
     */
    public record TicketDetails(Long customerId, String subject, String description, SupportTicket.Priority priority,
            SupportTicket.Status status) {
    }
}

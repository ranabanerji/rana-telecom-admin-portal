package com.example.telecom.service;

import com.example.telecom.model.Customer;
import com.example.telecom.model.SupportTicket;
import com.example.telecom.model.SupportTicket.Priority;
import com.example.telecom.model.SupportTicket.Status;
import com.example.telecom.repository.CustomerRepository;
import com.example.telecom.repository.SupportTicketRepository;
import com.example.telecom.service.TicketService.TicketDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static com.example.telecom.EntityFixtures.CLOCK;
import static com.example.telecom.EntityFixtures.NOW;
import static com.example.telecom.EntityFixtures.customer;
import static com.example.telecom.EntityFixtures.plan;
import static com.example.telecom.EntityFixtures.ticket;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TicketServiceTest {

    private static final Instant EARLIER = Instant.parse("2026-01-01T00:00:00Z");

    private final SupportTicketRepository tickets = mock(SupportTicketRepository.class);
    private final CustomerRepository customers = mock(CustomerRepository.class);
    private final TicketService service = new TicketService(tickets, customers, CLOCK);

    private Customer ada;

    @BeforeEach
    void setUp() {
        ada = customer(1L, plan(1L, "Basic", "20.00"));
        when(customers.findById(1L)).thenReturn(Optional.of(ada));
        when(tickets.save(any(SupportTicket.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    static Stream<Arguments> everyStatusChange() {
        return Arrays.stream(Status.values())
                .flatMap(from -> Arrays.stream(Status.values()).map(to -> Arguments.of(from, to)));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("everyStatusChange")
    void everyStatusChangeSetsKeepsOrClearsTheResolutionTime(Status from, Status to) {
        SupportTicket ticket = existing(from, from.isResolved() ? EARLIER : null);

        service.update(5L, details(null, to));

        assertEquals(to, ticket.getStatus());
        if (!to.isResolved()) {
            assertNull(ticket.getResolvedAt(), "an unresolved ticket has no resolution time");
        } else if (from.isResolved()) {
            assertEquals(EARLIER, ticket.getResolvedAt(), "moving between resolved statuses keeps the original");
        } else {
            assertEquals(NOW, ticket.getResolvedAt(), "resolving stamps the current time");
        }
    }

    @Test
    void reopenAndCloseFollowsTheTicketLifecycle() {
        SupportTicket ticket = existing(Status.OPEN, null);
        Instant resolved = Instant.parse("2026-11-25T10:00:00Z");
        Instant closed = Instant.parse("2026-11-26T09:30:00Z");

        service.update(5L, details(null, Status.RESOLVED));
        assertEquals(resolved, ticket.getResolvedAt());
        service.update(5L, details(null, Status.OPEN));
        assertNull(ticket.getResolvedAt());
        TicketService later = new TicketService(tickets, customers, Clock.fixed(closed, ZoneOffset.UTC));
        later.update(5L, details(null, Status.CLOSED));
        assertEquals(closed, ticket.getResolvedAt());
        later.update(5L, details(null, Status.RESOLVED));
        assertEquals(closed, ticket.getResolvedAt());
    }

    @Test
    void aResolvedTicketWithoutAResolutionTimeGetsOne() {
        SupportTicket ticket = existing(Status.CLOSED, null);

        service.update(5L, details(null, Status.CLOSED));

        assertEquals(NOW, ticket.getResolvedAt());
    }

    @Test
    void resolvingAnOpenTicketReplacesAStaleResolutionTime() {
        SupportTicket ticket = existing(Status.OPEN, EARLIER);

        service.update(5L, details(null, Status.RESOLVED));

        assertEquals(NOW, ticket.getResolvedAt());
    }

    @Test
    void updateKeepsStatusAndPriorityWhenOmitted() {
        SupportTicket ticket = existing(Status.RESOLVED, EARLIER);
        ticket.setPriority(Priority.HIGH);

        service.update(5L, new TicketDetails(null, "Billing", null, null, null));

        assertEquals(Status.RESOLVED, ticket.getStatus());
        assertEquals(EARLIER, ticket.getResolvedAt());
        assertEquals(Priority.HIGH, ticket.getPriority());
        assertEquals("Billing", ticket.getSubject());
        assertEquals("", ticket.getDescription());
    }

    @Test
    void createStartsOpenForAnExistingCustomer() {
        SupportTicket created = service.create(new TicketDetails(1L, "Roaming", null, Priority.LOW, Status.CLOSED));

        assertSame(ada, created.getCustomer());
        assertEquals(Status.OPEN, created.getStatus());
        assertNull(created.getResolvedAt());
        assertEquals("", created.getDescription());
        assertEquals(Priority.LOW, created.getPriority());
    }

    @Test
    void createRequiresAnExistingCustomer() {
        when(customers.findById(99L)).thenReturn(Optional.empty());

        assertEquals("customerId", assertThrows(InvalidInputException.class,
                () -> service.create(new TicketDetails(null, "Help", null, Priority.LOW, null))).getField());
        assertEquals("customerId", assertThrows(InvalidInputException.class,
                () -> service.create(new TicketDetails(99L, "Help", null, Priority.LOW, null))).getField());
        verify(tickets, never()).save(any(SupportTicket.class));
    }

    @Test
    void searchWithoutFiltersUsesTheNewestFirstQuery() {
        PageRequest page = PageRequest.of(0, 50);
        PageImpl<SupportTicket> result = new PageImpl<>(List.of());
        when(tickets.findAllByOrderByCreatedAtDescIdDesc(page)).thenReturn(result);

        assertSame(result, service.search(null, null, null, page));
        verify(tickets, never()).findFiltered(any(), any(), any(), any());
    }

    @Test
    void searchWithAnyFilterUsesTheFilteredQuery() {
        PageRequest page = PageRequest.of(1, 10);
        PageImpl<SupportTicket> result = new PageImpl<>(List.of());
        when(tickets.findFiltered(null, Status.OPEN, null, page)).thenReturn(result);

        assertSame(result, service.search(null, Status.OPEN, null, page));
    }

    @Test
    void getUpdateAndDeleteReportAMissingTicket() {
        when(tickets.findById(anyLong())).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.get(404L));
        assertThrows(NotFoundException.class, () -> service.update(404L, details(null, null)));
        assertThrows(NotFoundException.class, () -> service.delete(404L));
        verify(tickets, never()).deleteById(anyLong());
    }

    @Test
    void deleteRemovesAnExistingTicket() {
        when(tickets.existsById(5L)).thenReturn(true);

        service.delete(5L);

        verify(tickets).deleteById(5L);
    }

    private SupportTicket existing(Status status, Instant resolvedAt) {
        SupportTicket ticket = ticket(5L, ada);
        ticket.setStatus(status);
        ticket.setResolvedAt(resolvedAt);
        when(tickets.findById(5L)).thenReturn(Optional.of(ticket));
        return ticket;
    }

    private static TicketDetails details(Priority priority, Status status) {
        return new TicketDetails(null, "Help", "Details", priority, status);
    }
}

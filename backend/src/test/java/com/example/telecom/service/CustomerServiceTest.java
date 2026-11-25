package com.example.telecom.service;

import com.example.telecom.model.Customer;
import com.example.telecom.model.Plan;
import com.example.telecom.repository.CustomerRepository;
import com.example.telecom.repository.DeviceRepository;
import com.example.telecom.repository.PlanRepository;
import com.example.telecom.repository.SupportTicketRepository;
import com.example.telecom.repository.UsageRecordRepository;
import com.example.telecom.service.CustomerService.CustomerDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.Optional;

import static com.example.telecom.EntityFixtures.customer;
import static com.example.telecom.EntityFixtures.plan;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomerServiceTest {

    private final CustomerRepository customers = mock(CustomerRepository.class);
    private final PlanRepository plans = mock(PlanRepository.class);
    private final SupportTicketRepository tickets = mock(SupportTicketRepository.class);
    private final UsageRecordRepository usage = mock(UsageRecordRepository.class);
    private final DeviceRepository devices = mock(DeviceRepository.class);
    private final CustomerService service = new CustomerService(customers, plans, tickets, usage, devices);

    private Plan standard;

    @BeforeEach
    void setUp() {
        standard = plan(2L, "Standard", "40.00");
        when(plans.findById(2L)).thenReturn(Optional.of(standard));
        when(customers.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createRequiresAPlan() {
        InvalidInputException error = assertThrows(InvalidInputException.class,
                () -> service.create(details(null, null, null)));

        assertEquals("planId", error.getField());
        verify(customers, never()).save(any(Customer.class));
    }

    @Test
    void createRejectsAnUnknownPlan() {
        when(plans.findById(99L)).thenReturn(Optional.empty());

        InvalidInputException error = assertThrows(InvalidInputException.class,
                () -> service.create(details(99L, null, null)));

        assertEquals("planId", error.getField());
        verify(customers, never()).save(any(Customer.class));
    }

    @Test
    void createStartsActiveOnThePlanAndIgnoresTheRequestedStatus() {
        Customer created = service.create(details(2L, Customer.Status.CANCELLED, new BigDecimal("12.34")));

        assertEquals(Customer.Status.ACTIVE, created.getStatus());
        assertSame(standard, created.getPlan());
        assertEquals(new BigDecimal("12.34"), created.getBalance());
    }

    @Test
    void createDefaultsTheBalanceToZero() {
        assertEquals(BigDecimal.ZERO, service.create(details(2L, null, null)).getBalance());
    }

    @Test
    void updateChangesFieldsStatusAndPlan() {
        Customer existing = customer(1L, plan(1L, "Basic", "20.00"));
        when(customers.findById(1L)).thenReturn(Optional.of(existing));

        Customer updated = service.update(1L, details(2L, Customer.Status.SUSPENDED, BigDecimal.TEN));

        assertEquals("Ada", updated.getFirstName());
        assertEquals("ada@example.com", updated.getEmail());
        assertSame(standard, updated.getPlan());
        assertEquals(Customer.Status.SUSPENDED, updated.getStatus());
        assertEquals(BigDecimal.TEN, updated.getBalance());
    }

    @Test
    void updateKeepsPlanStatusAndBalanceWhenOmitted() {
        Plan basic = plan(1L, "Basic", "20.00");
        Customer existing = customer(1L, basic);
        existing.setStatus(Customer.Status.SUSPENDED);
        existing.setBalance(BigDecimal.ONE);
        when(customers.findById(1L)).thenReturn(Optional.of(existing));

        Customer updated = service.update(1L, details(null, null, null));

        assertSame(basic, updated.getPlan());
        assertEquals(Customer.Status.SUSPENDED, updated.getStatus());
        assertEquals(BigDecimal.ONE, updated.getBalance());
    }

    @Test
    void updateWithAnUnknownPlanChangesNothing() {
        Customer existing = customer(1L, standard);
        when(customers.findById(1L)).thenReturn(Optional.of(existing));
        when(plans.findById(99L)).thenReturn(Optional.empty());

        assertThrows(InvalidInputException.class, () -> service.update(1L, details(99L, null, null)));

        assertEquals("First1", existing.getFirstName());
        verify(customers, never()).save(any(Customer.class));
    }

    @Test
    void updateAndGetReportAMissingCustomer() {
        when(customers.findById(anyLong())).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.update(404L, details(null, null, null)));
        assertThrows(NotFoundException.class, () -> service.get(404L));
    }

    @ParameterizedTest
    @ValueSource(strings = {"tickets", "usage", "devices"})
    void deleteIsRejectedWhileDependentsExist(String dependent) {
        when(customers.existsById(1L)).thenReturn(true);
        when(tickets.existsByCustomerId(1L)).thenReturn("tickets".equals(dependent));
        when(usage.existsByCustomerId(1L)).thenReturn("usage".equals(dependent));
        when(devices.existsByCustomerId(1L)).thenReturn("devices".equals(dependent));

        ConflictException error = assertThrows(ConflictException.class, () -> service.delete(1L));

        assertEquals("dependencies", error.getField());
        verify(customers, never()).deleteById(anyLong());
    }

    @Test
    void deleteRemovesACustomerWithoutDependents() {
        when(customers.existsById(1L)).thenReturn(true);

        service.delete(1L);

        verify(customers).deleteById(1L);
    }

    @Test
    void deleteReportsAMissingCustomer() {
        assertThrows(NotFoundException.class, () -> service.delete(404L));

        verify(customers, never()).deleteById(anyLong());
    }

    private static CustomerDetails details(Long planId, Customer.Status status, BigDecimal balance) {
        return new CustomerDetails("Ada", "Lovelace", "ada@example.com", "555-0100", planId, status, balance);
    }
}

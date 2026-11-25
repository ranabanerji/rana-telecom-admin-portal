package com.example.telecom.service;

import com.example.telecom.model.Customer;
import com.example.telecom.model.Device;
import com.example.telecom.model.SupportTicket;
import com.example.telecom.repository.CustomerRepository;
import com.example.telecom.repository.DeviceRepository;
import com.example.telecom.repository.SupportTicketRepository;
import com.example.telecom.service.DashboardService.DashboardStats;
import com.example.telecom.service.DashboardService.PlanCount;
import com.example.telecom.service.DashboardService.PlanRevenue;
import com.example.telecom.service.DashboardService.StatusCount;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DashboardServiceTest {

    private final CustomerRepository customers = mock(CustomerRepository.class);
    private final DeviceRepository devices = mock(DeviceRepository.class);
    private final SupportTicketRepository tickets = mock(SupportTicketRepository.class);
    private final DashboardService service = new DashboardService(customers, devices, tickets);

    @Test
    void statsComeFromAggregateQueriesNotEntityLists() {
        when(customers.countByStatus(Customer.Status.ACTIVE)).thenReturn(3L);
        when(customers.aggregateActiveByPlan())
                .thenReturn(List.of(row(1L, "Basic", "20.00", 2L), row(2L, "Standard", "40.00", 1L)));
        when(tickets.countByStatusIn(any())).thenReturn(5L);
        when(devices.countByStatus(Device.Status.ASSIGNED)).thenReturn(7L);

        DashboardStats stats = service.stats();

        assertEquals(3, stats.activeCustomers());
        assertEquals(new BigDecimal("80.00"), stats.monthlyRevenue());
        assertEquals(5, stats.openTickets());
        assertEquals(7, stats.devicesInUse());
        verify(customers, never()).findAll();
        verify(customers, never()).findAll(any(Pageable.class));
    }

    @Test
    void statsTolerateNoActiveCustomers() {
        when(customers.aggregateActiveByPlan()).thenReturn(List.of());

        assertEquals(BigDecimal.ZERO, service.stats().monthlyRevenue());
    }

    @Test
    void duplicatePlanNamesKeepSeparateCountsAndRevenue() {
        when(customers.aggregateActiveByPlan())
                .thenReturn(List.of(row(1L, "Basic", "20.00", 2L), row(2L, "Basic", "35.00", 1L)));

        List<PlanCount> counts = service.customersByPlan();
        List<PlanRevenue> revenue = service.revenueByPlan();

        assertEquals(List.of(new PlanCount(1L, "Basic", 2), new PlanCount(2L, "Basic", 1)), counts);
        assertEquals(List.of(new PlanRevenue(1L, "Basic", new BigDecimal("40.00")),
                new PlanRevenue(2L, "Basic", new BigDecimal("35.00"))), revenue);
    }

    @Test
    void statusBreakdownsNameTheEnumValues() {
        when(devices.countByStatus()).thenReturn(
                List.of(new Object[] {Device.Status.ASSIGNED, 7L}, new Object[] {Device.Status.AVAILABLE, 3L}));
        when(tickets.countByStatus()).thenReturn(
                List.of(new Object[] {SupportTicket.Status.OPEN, 4L}, new Object[] {SupportTicket.Status.CLOSED, 9L}));

        assertEquals(List.of(new StatusCount("ASSIGNED", 7), new StatusCount("AVAILABLE", 3)),
                service.devicesByStatus());
        assertEquals(List.of(new StatusCount("OPEN", 4), new StatusCount("CLOSED", 9)), service.ticketsByStatus());
    }

    private static Object[] row(long planId, String planName, String monthlyPrice, long activeCustomers) {
        return new Object[] {planId, planName, new BigDecimal(monthlyPrice), activeCustomers};
    }
}

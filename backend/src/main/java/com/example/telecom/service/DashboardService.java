package com.example.telecom.service;

import com.example.telecom.model.Customer;
import com.example.telecom.model.Device;
import com.example.telecom.model.SupportTicket;
import com.example.telecom.repository.CustomerRepository;
import com.example.telecom.repository.DeviceRepository;
import com.example.telecom.repository.SupportTicketRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Dashboard aggregates. Every number comes from a COUNT or GROUP BY query, so
 * no endpoint loads entity lists into memory. Plan aggregates are keyed by plan
 * id, which keeps duplicate plan names separate.
 */
@Service
@Transactional(readOnly = true)
public class DashboardService {

    private final CustomerRepository customers;
    private final DeviceRepository devices;
    private final SupportTicketRepository tickets;

    public DashboardService(CustomerRepository customers, DeviceRepository devices, SupportTicketRepository tickets) {
        this.customers = customers;
        this.devices = devices;
        this.tickets = tickets;
    }

    public DashboardStats stats() {
        BigDecimal monthlyRevenue = aggregateActiveByPlan().stream().map(PlanAggregate::revenue).reduce(BigDecimal.ZERO,
                BigDecimal::add);
        return new DashboardStats(customers.countByStatus(Customer.Status.ACTIVE), monthlyRevenue,
                tickets.countByStatusIn(List.of(SupportTicket.Status.OPEN, SupportTicket.Status.IN_PROGRESS)),
                devices.countByStatus(Device.Status.ASSIGNED));
    }

    public List<PlanCount> customersByPlan() {
        return aggregateActiveByPlan().stream().map(row -> new PlanCount(row.planId(), row.planName(), row.customers()))
                .toList();
    }

    public List<PlanRevenue> revenueByPlan() {
        return aggregateActiveByPlan().stream().map(row -> new PlanRevenue(row.planId(), row.planName(), row.revenue()))
                .toList();
    }

    public List<StatusCount> devicesByStatus() {
        return devices.countByStatus().stream()
                .map(row -> new StatusCount(((Device.Status) row[0]).name(), ((Number) row[1]).longValue())).toList();
    }

    public List<StatusCount> ticketsByStatus() {
        return tickets.countByStatus().stream()
                .map(row -> new StatusCount(((SupportTicket.Status) row[0]).name(), ((Number) row[1]).longValue()))
                .toList();
    }

    private List<PlanAggregate> aggregateActiveByPlan() {
        return customers.aggregateActiveByPlan().stream().map(DashboardService::toAggregate).toList();
    }

    private static PlanAggregate toAggregate(Object[] row) {
        return new PlanAggregate(((Number) row[0]).longValue(), (String) row[1], (BigDecimal) row[2],
                ((Number) row[3]).longValue());
    }

    public record DashboardStats(long activeCustomers, BigDecimal monthlyRevenue, long openTickets, long devicesInUse) {
    }

    public record StatusCount(String name, long value) {
    }

    public record PlanCount(long planId, String name, long customers) {
    }

    public record PlanRevenue(long planId, String name, BigDecimal revenue) {
    }

    /**
     * One GROUP BY row: plan id, display name, monthly price and active subscriber
     * count. Revenue is price times count in {@link BigDecimal}; SQLite's SUM
     * returns a REAL, which would round two-decimal prices.
     */
    private record PlanAggregate(long planId, String planName, BigDecimal monthlyPrice, long customers) {
        BigDecimal revenue() {
            return monthlyPrice.multiply(BigDecimal.valueOf(customers));
        }
    }
}

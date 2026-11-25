package com.example.telecom.controller;

import com.example.telecom.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping("/stats")
    public DashboardStats getStats() {
        DashboardService.DashboardStats stats = service.stats();
        return new DashboardStats(stats.activeCustomers(), stats.monthlyRevenue(), stats.openTickets(),
                stats.devicesInUse());
    }

    @GetMapping("/customers-by-plan")
    public List<PlanChartData> getCustomersByPlan() {
        return service.customersByPlan().stream()
                .map(row -> new PlanChartData(row.planId(), row.name(), row.customers())).toList();
    }

    @GetMapping("/devices-by-status")
    public List<ChartData> getDevicesByStatus() {
        return service.devicesByStatus().stream().map(row -> new ChartData(row.name(), row.value())).toList();
    }

    @GetMapping("/tickets-by-status")
    public List<ChartData> getTicketsByStatus() {
        return service.ticketsByStatus().stream().map(row -> new ChartData(row.name(), row.value())).toList();
    }

    @GetMapping("/revenue-by-plan")
    public List<RevenueData> getRevenueByPlan() {
        return service.revenueByPlan().stream().map(row -> new RevenueData(row.planId(), row.name(), row.revenue()))
                .toList();
    }

    public record DashboardStats(long active_customers, BigDecimal monthly_revenue, long open_tickets,
            long devices_in_use) {
    }

    public record ChartData(String name, long value) {
    }

    public record PlanChartData(long plan_id, String name, long value) {
    }

    public record RevenueData(long plan_id, String name, BigDecimal revenue) {
    }
}

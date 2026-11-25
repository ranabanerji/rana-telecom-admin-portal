package com.example.telecom.controller;

import com.example.telecom.service.DashboardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP mapping only; the aggregation rules are covered by DashboardServiceTest.
 */
class DashboardControllerTest {

    private final DashboardService service = mock(DashboardService.class);

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new DashboardController(service))
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }

    @Test
    void statsMapsTheServiceResult() throws Exception {
        when(service.stats()).thenReturn(new DashboardService.DashboardStats(3, new BigDecimal("80.00"), 5, 7));

        mockMvc.perform(get("/api/dashboard/stats")).andExpect(status().isOk())
                .andExpect(jsonPath("$.active_customers").value(3))
                .andExpect(jsonPath("$.monthly_revenue").value(80.00)).andExpect(jsonPath("$.open_tickets").value(5))
                .andExpect(jsonPath("$.devices_in_use").value(7));
    }

    @Test
    void customersByPlanMapsTheServiceRows() throws Exception {
        when(service.customersByPlan()).thenReturn(List.of(new DashboardService.PlanCount(1, "Basic", 2),
                new DashboardService.PlanCount(2, "Standard", 1)));

        mockMvc.perform(get("/api/dashboard/customers-by-plan")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2)).andExpect(jsonPath("$[0].plan_id").value(1))
                .andExpect(jsonPath("$[0].name").value("Basic")).andExpect(jsonPath("$[0].value").value(2));

        verify(service).customersByPlan();
    }

    @Test
    void devicesByStatusMapsTheServiceRows() throws Exception {
        when(service.devicesByStatus()).thenReturn(List.of(new DashboardService.StatusCount("ASSIGNED", 7),
                new DashboardService.StatusCount("AVAILABLE", 3)));

        mockMvc.perform(get("/api/dashboard/devices-by-status")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("ASSIGNED")).andExpect(jsonPath("$[1].value").value(3));
    }

    @Test
    void ticketsByStatusMapsTheServiceRows() throws Exception {
        when(service.ticketsByStatus()).thenReturn(
                List.of(new DashboardService.StatusCount("OPEN", 4), new DashboardService.StatusCount("CLOSED", 9)));

        mockMvc.perform(get("/api/dashboard/tickets-by-status")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("OPEN")).andExpect(jsonPath("$[1].value").value(9));
    }

    @Test
    void revenueByPlanMapsTheServiceRows() throws Exception {
        when(service.revenueByPlan())
                .thenReturn(List.of(new DashboardService.PlanRevenue(1, "Basic", new BigDecimal("40.00")),
                        new DashboardService.PlanRevenue(2, "Standard", new BigDecimal("40.00"))));

        mockMvc.perform(get("/api/dashboard/revenue-by-plan")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2)).andExpect(jsonPath("$[0].plan_id").value(1))
                .andExpect(jsonPath("$[0].revenue").value(40.00));
    }
}

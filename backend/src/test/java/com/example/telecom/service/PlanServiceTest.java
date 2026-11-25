package com.example.telecom.service;

import com.example.telecom.model.Plan;
import com.example.telecom.repository.CustomerRepository;
import com.example.telecom.repository.PlanRepository;
import com.example.telecom.service.PlanService.PlanDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static com.example.telecom.EntityFixtures.plan;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlanServiceTest {

    private final PlanRepository plans = mock(PlanRepository.class);
    private final CustomerRepository customers = mock(CustomerRepository.class);
    private final PlanService service = new PlanService(plans, customers);

    @BeforeEach
    void setUp() {
        when(plans.save(any(Plan.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createStartsActiveAndIgnoresTheRequestedFlag() {
        Plan created = service.create(details(false));

        assertTrue(created.getIsActive());
        assertEquals("Student", created.getName());
        assertEquals(new BigDecimal("15.50"), created.getMonthlyPrice());
    }

    @Test
    void updateKeepsTheActiveFlagWhenOmitted() {
        Plan existing = plan(3L, "Premium", "60.00");
        existing.setIsActive(false);
        when(plans.findById(3L)).thenReturn(Optional.of(existing));

        Plan updated = service.update(3L, details(null));

        assertFalse(updated.getIsActive());
        assertEquals("Student", updated.getName());
        assertEquals(5, updated.getDataLimitGb());
    }

    @Test
    void updateCanDeactivateAPlan() {
        when(plans.findById(3L)).thenReturn(Optional.of(plan(3L, "Premium", "60.00")));

        assertFalse(service.update(3L, details(false)).getIsActive());
    }

    @Test
    void updateAndGetReportAMissingPlan() {
        when(plans.findById(anyLong())).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.update(404L, details(null)));
        assertThrows(NotFoundException.class, () -> service.get(404L));
    }

    @Test
    void findActiveUsesTheActiveOnlyQuery() {
        List<Plan> active = List.of(plan(1L, "Basic", "20.00"));
        when(plans.findByIsActiveTrue()).thenReturn(active);

        assertEquals(active, service.findActive());
    }

    @Test
    void deleteIsRejectedWhileCustomersUseThePlan() {
        when(plans.existsById(4L)).thenReturn(true);
        when(customers.existsByPlanId(4L)).thenReturn(true);

        ConflictException error = assertThrows(ConflictException.class, () -> service.delete(4L));

        assertEquals("planId", error.getField());
        assertTrue(error.getMessage().contains("Deactivate"));
        verify(plans, never()).deleteById(anyLong());
    }

    @Test
    void deleteRemovesAnUnusedPlan() {
        when(plans.existsById(4L)).thenReturn(true);

        service.delete(4L);

        verify(plans).deleteById(4L);
    }

    @Test
    void deleteReportsAMissingPlan() {
        assertThrows(NotFoundException.class, () -> service.delete(404L));

        verify(plans, never()).deleteById(anyLong());
    }

    private static PlanDetails details(Boolean isActive) {
        return new PlanDetails("Student", new BigDecimal("15.50"), 5, 300, 300, isActive);
    }
}

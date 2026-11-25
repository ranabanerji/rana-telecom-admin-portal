package com.example.telecom.controller;

import com.example.telecom.model.Plan;
import com.example.telecom.service.ConflictException;
import com.example.telecom.service.NotFoundException;
import com.example.telecom.service.PlanService;
import com.example.telecom.service.PlanService.PlanDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static com.example.telecom.EntityFixtures.plan;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HTTP mapping only; the plan rules are covered by PlanServiceTest. */
class PlanControllerTest {

    private static final String BODY = """
            {"name":"Student","monthlyPrice":15.50,"dataLimitGb":5,"minutesLimit":300,"smsLimit":300,"isActive":false}
            """;
    private static final PlanDetails DETAILS = new PlanDetails("Student", new BigDecimal("15.50"), 5, 300, 300, false);

    private final PlanService service = mock(PlanService.class);

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new PlanController(service))
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }

    @Test
    void listReturnsSnakeCaseFieldsWithTheTotalCount() throws Exception {
        PageRequest firstPage = PageRequest.of(0, 100, Sort.by("id"));
        when(service.findPage(firstPage))
                .thenReturn(new PageImpl<>(List.of(plan(1L, "Basic", "20.00")), firstPage, 106));

        mockMvc.perform(get("/api/plans")).andExpect(status().isOk()).andExpect(header().string("X-Total-Count", "106"))
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Basic")).andExpect(jsonPath("$[0].monthly_price").value(20.00))
                .andExpect(jsonPath("$[0].data_limit_gb").value(10))
                .andExpect(jsonPath("$[0].minutes_limit").value(500)).andExpect(jsonPath("$[0].sms_limit").value(500))
                .andExpect(jsonPath("$[0].is_active").value(true));
    }

    @Test
    void activeListsTheServicesActivePlans() throws Exception {
        when(service.findActive()).thenReturn(List.of(plan(2L, "Standard", "40.00")));

        mockMvc.perform(get("/api/plans/active")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Standard"));
    }

    @Test
    void getReturnsNotFoundForAnUnknownId() throws Exception {
        when(service.get(anyLong())).thenThrow(new NotFoundException("Plan", 404L));

        mockMvc.perform(get("/api/plans/404")).andExpect(status().isNotFound());
    }

    @Test
    void createPassesTheRequestToTheService() throws Exception {
        Plan saved = plan(9L, "Student", "15.50");
        when(service.create(DETAILS)).thenReturn(saved);

        mockMvc.perform(post("/api/plans").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(9))
                .andExpect(jsonPath("$.monthly_price").value(15.50));
    }

    @Test
    void createRejectsMissingFieldsBeforeCallingTheService() throws Exception {
        mockMvc.perform(post("/api/plans").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.monthlyPrice").exists());

        verify(service, never()).create(any());
    }

    @Test
    void updatePassesTheIdAndRequestToTheService() throws Exception {
        Plan saved = plan(3L, "Student", "15.50");
        saved.setIsActive(false);
        when(service.update(3L, DETAILS)).thenReturn(saved);

        mockMvc.perform(put("/api/plans/3").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk()).andExpect(jsonPath("$.is_active").value(false));
    }

    @Test
    void deleteReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/plans/4")).andExpect(status().isNoContent());

        verify(service).delete(4L);
    }

    @Test
    void deleteReportsConflictsAndMissingPlans() throws Exception {
        doThrow(new ConflictException("planId", "Deactivate it instead")).when(service).delete(4L);
        doThrow(new NotFoundException("Plan", 404L)).when(service).delete(404L);

        mockMvc.perform(delete("/api/plans/4")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors.planId").exists());
        mockMvc.perform(delete("/api/plans/404")).andExpect(status().isNotFound());
    }
}

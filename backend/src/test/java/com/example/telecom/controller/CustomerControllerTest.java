package com.example.telecom.controller;

import com.example.telecom.model.Customer;
import com.example.telecom.service.ConflictException;
import com.example.telecom.service.CustomerService;
import com.example.telecom.service.CustomerService.CustomerDetails;
import com.example.telecom.service.InvalidInputException;
import com.example.telecom.service.NotFoundException;
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

import static com.example.telecom.EntityFixtures.customer;
import static com.example.telecom.EntityFixtures.plan;
import static org.hamcrest.Matchers.nullValue;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HTTP mapping only; the customer rules are covered by CustomerServiceTest. */
class CustomerControllerTest {

    private static final String BODY = """
            {"firstName":"Ada","lastName":"Lovelace","email":"ada@example.com","phone":"555-0100",
             "planId":2,"status":"SUSPENDED","balance":12.34}
            """;
    private static final CustomerDetails DETAILS = new CustomerDetails("Ada", "Lovelace", "ada@example.com", "555-0100",
            2L, Customer.Status.SUSPENDED, new BigDecimal("12.34"));

    private final CustomerService service = mock(CustomerService.class);

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new CustomerController(service))
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }

    @Test
    void listReturnsTheFirstPageWithTheTotalCount() throws Exception {
        PageRequest firstPage = PageRequest.of(0, 1000, Sort.by("id"));
        when(service.findPage(firstPage))
                .thenReturn(new PageImpl<>(List.of(customer(1L, plan(2L, "Standard", "40.00"))), firstPage, 1201));

        mockMvc.perform(get("/api/customers")).andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "1201")).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].first_name").value("First1"))
                .andExpect(jsonPath("$[0].last_name").value("Last1")).andExpect(jsonPath("$[0].plan_id").value(2))
                .andExpect(jsonPath("$[0].plan_name").value("Standard"))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));
    }

    @Test
    void listToleratesAMissingPlan() throws Exception {
        when(service.findPage(any())).thenReturn(new PageImpl<>(List.of(customer(1L, null))));

        mockMvc.perform(get("/api/customers")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].plan_id").value(nullValue()))
                .andExpect(jsonPath("$[0].plan_name").value(nullValue()));
    }

    @Test
    void getReturnsNotFoundAsAProblem() throws Exception {
        when(service.get(anyLong())).thenThrow(new NotFoundException("Customer", 404L));

        mockMvc.perform(get("/api/customers/404")).andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors.id").exists());
    }

    @Test
    void createPassesTheParsedRequestToTheService() throws Exception {
        Customer saved = customer(11L, plan(2L, "Standard", "40.00"));
        when(service.create(DETAILS)).thenReturn(saved);

        mockMvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(11))
                .andExpect(jsonPath("$.plan_name").value("Standard"));
    }

    @Test
    void createReportsServiceRuleViolationsAgainstTheField() throws Exception {
        when(service.create(any())).thenThrow(new InvalidInputException("planId", "planId is required"));

        mockMvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.planId").value("planId is required"));
    }

    @Test
    void createRejectsAnUnknownStatusBeforeCallingTheService() throws Exception {
        mockMvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON)
                .content(BODY.replace("SUSPENDED", "bogus"))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.status").exists());

        verify(service, never()).create(any());
    }

    @Test
    void createRejectsMissingFieldsBeforeCallingTheService() throws Exception {
        mockMvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.firstName").exists());

        verify(service, never()).create(any());
    }

    @Test
    void updatePassesTheIdAndParsedRequestToTheService() throws Exception {
        Customer saved = customer(1L, plan(2L, "Standard", "40.00"));
        saved.setStatus(Customer.Status.SUSPENDED);
        when(service.update(1L, DETAILS)).thenReturn(saved);

        mockMvc.perform(put("/api/customers/1").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUSPENDED"));
    }

    @Test
    void updateReturnsNotFoundForAnUnknownId() throws Exception {
        when(service.update(anyLong(), any())).thenThrow(new NotFoundException("Customer", 404L));

        mockMvc.perform(put("/api/customers/404").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/customers/1")).andExpect(status().isNoContent());

        verify(service).delete(1L);
    }

    @Test
    void deleteReportsConflictsAndMissingCustomers() throws Exception {
        doThrow(new ConflictException("dependencies", "Customer has tickets")).when(service).delete(1L);
        doThrow(new NotFoundException("Customer", 404L)).when(service).delete(404L);

        mockMvc.perform(delete("/api/customers/1")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors.dependencies").exists());
        mockMvc.perform(delete("/api/customers/404")).andExpect(status().isNotFound());
    }
}

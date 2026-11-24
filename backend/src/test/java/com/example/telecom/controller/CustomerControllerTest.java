package com.example.telecom.controller;

import com.example.telecom.model.Customer;
import com.example.telecom.model.Plan;
import com.example.telecom.repository.CustomerRepository;
import com.example.telecom.repository.PlanRepository;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CustomerControllerTest {

    private static final String CUSTOMERS_URL = "/api/customers";
    private static final String STANDARD = "Standard";

    private final CustomerRepository repository = Mockito.mock(CustomerRepository.class);
    private final PlanRepository planRepository = Mockito.mock(PlanRepository.class);

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new CustomerController(repository, planRepository)).build();
    }

    @Test
    void listCustomersFlattensPlanOntoTheResponse() throws Exception {
        when(repository.findAll(ArgumentMatchers.any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(customer(1L, plan(2L, STANDARD, "40.00")))));

        mockMvc.perform(get(CUSTOMERS_URL)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].first_name").value("First1"))
                .andExpect(jsonPath("$[0].last_name").value("Last1")).andExpect(jsonPath("$[0].plan_id").value(2))
                .andExpect(jsonPath("$[0].plan_name").value(STANDARD))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));
    }

    @Test
    void listCustomersToleratesAMissingPlan() throws Exception {
        when(repository.findAll(ArgumentMatchers.any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(customer(1L, null))));

        mockMvc.perform(get(CUSTOMERS_URL)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].plan_id").value(Matchers.nullValue()))
                .andExpect(jsonPath("$[0].plan_name").value(Matchers.nullValue()));
    }

    @Test
    void fetchCustomerReturnsNotFoundForUnknownId() throws Exception {
        when(repository.findById(ArgumentMatchers.anyLong())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/customers/404")).andExpect(status().isNotFound());
    }

    @Test
    void createCustomerRejectsAnUnknownPlan() throws Exception {
        when(planRepository.findById(99L)).thenReturn(Optional.empty());

        String body = """
                {"firstName":"Ada","lastName":"Lovelace","email":"ada@example.com","phone":"555-0100","planId":99}
                """;

        mockMvc.perform(
                MockMvcRequestBuilders.post(CUSTOMERS_URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());

        Mockito.verify(repository, Mockito.never()).save(ArgumentMatchers.any(Customer.class));
    }

    @Test
    void createCustomerStartsActiveWithTheGivenBalance() throws Exception {
        when(planRepository.findById(2L)).thenReturn(Optional.of(plan(2L, STANDARD, "40.00")));
        when(repository.save(ArgumentMatchers.any(Customer.class))).thenAnswer(invocation -> {
            Customer saved = invocation.getArgument(0);
            saved.setId(11L);
            return saved;
        });

        String body = """
                {"firstName":"Ada","lastName":"Lovelace","email":"ada@example.com","phone":"555-0100",
                 "planId":2,"balance":12.34}
                """;

        mockMvc.perform(
                MockMvcRequestBuilders.post(CUSTOMERS_URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(11))
                .andExpect(jsonPath("$.status").value("ACTIVE")).andExpect(jsonPath("$.balance").value(12.34))
                .andExpect(jsonPath("$.plan_name").value(STANDARD));
    }

    @Test
    void updateCustomerAppliesAStatusTransition() throws Exception {
        when(repository.findById(1L)).thenReturn(Optional.of(customer(1L, plan(2L, STANDARD, "40.00"))));
        when(repository.save(ArgumentMatchers.any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String body = """
                {"firstName":"First1","lastName":"Last1","email":"customer1@example.com","phone":"555-0001",
                 "status":"SUSPENDED"}
                """;

        mockMvc.perform(
                MockMvcRequestBuilders.put("/api/customers/1").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUSPENDED"));
    }

    @Test
    void updateCustomerReturnsNotFoundForUnknownId() throws Exception {
        when(repository.findById(ArgumentMatchers.anyLong())).thenReturn(Optional.empty());

        String body = """
                {"firstName":"Ada","lastName":"Lovelace","email":"ada@example.com","phone":"555-0100"}
                """;

        mockMvc.perform(
                MockMvcRequestBuilders.put("/api/customers/404").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteCustomerReturnsNoContentWhenPresent() throws Exception {
        when(repository.existsById(1L)).thenReturn(true);

        mockMvc.perform(MockMvcRequestBuilders.delete("/api/customers/1")).andExpect(status().isNoContent());

        Mockito.verify(repository).deleteById(1L);
    }

    @Test
    void deleteCustomerReturnsNotFoundWhenMissing() throws Exception {
        when(repository.existsById(404L)).thenReturn(false);

        mockMvc.perform(MockMvcRequestBuilders.delete("/api/customers/404")).andExpect(status().isNotFound());

        Mockito.verify(repository, Mockito.never()).deleteById(ArgumentMatchers.anyLong());
    }

    private static Plan plan(Long id, String name, String price) {
        Plan plan = new Plan(name, new BigDecimal(price), 10, 500, 500);
        plan.setId(id);
        return plan;
    }

    private static Customer customer(Long id, Plan plan) {
        Customer customer = new Customer("First" + id, "Last" + id, "customer" + id + "@example.com", "555-000" + id,
                plan);
        customer.setId(id);
        return customer;
    }
}

package com.example.telecom.security;

import com.example.telecom.model.Customer;
import com.example.telecom.model.Plan;
import com.example.telecom.model.SupportTicket;
import com.example.telecom.repository.CustomerRepository;
import com.example.telecom.repository.DeviceRepository;
import com.example.telecom.repository.PlanRepository;
import com.example.telecom.repository.SupportTicketRepository;
import com.example.telecom.repository.UsageRecordRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the real security filter chain: session login, CSRF, and the
 * ADMIN/AGENT permission matrix.
 */
@SpringBootTest
class SecurityIntegrationTest {

    private static final String ADMIN_USERNAME = "test-admin";
    private static final String AGENT_USERNAME = "test-agent";
    // Generated per run so no credential is ever committed.
    private static final String ADMIN_PASSWORD = UUID.randomUUID().toString();
    private static final String AGENT_PASSWORD = UUID.randomUUID().toString();

    private static final String LOGIN_URL = "/api/auth/login";
    private static final String CUSTOMERS = "/api/customers";
    private static final String TICKETS = "/api/tickets";
    private static final String CUSTOMER_PATH = "/api/customers/{id}";
    private static final String TICKET_PATH = "/api/tickets/{id}";
    private static final String ROLE_FIELD = "$.role";
    private static final String USERNAME_PARAM = "username";
    private static final String PASSWORD_PARAM = "password";
    private static final String XSRF_COOKIE = "XSRF-TOKEN";

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private PlanRepository planRepository;
    @Autowired
    private CustomerRepository customerRepository;
    @Autowired
    private SupportTicketRepository ticketRepository;
    @Autowired
    private DeviceRepository deviceRepository;
    @Autowired
    private UsageRecordRepository usageRepository;

    private MockMvc mockMvc;
    private Cookie xsrfCookie;
    private Plan plan;
    private Customer customer;
    private SupportTicket ticket;

    @DynamicPropertySource
    static void securityProperties(DynamicPropertyRegistry registry) {
        // In-memory SQLite gives each pooled connection its own empty database, so use
        // a throwaway file.
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + tempDatabase());
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.sql.init.mode", () -> "always");
        registry.add("spring.sql.init.schema-locations", () -> "classpath:seeded-schema.sql");
        registry.add("app.auth.admin.username", () -> ADMIN_USERNAME);
        registry.add("app.auth.admin.password", () -> ADMIN_PASSWORD);
        registry.add("app.auth.admin.name", () -> "Test Admin");
        registry.add("app.auth.admin.email", () -> "test-admin@example.com");
        registry.add("app.auth.agent.username", () -> AGENT_USERNAME);
        registry.add("app.auth.agent.password", () -> AGENT_PASSWORD);
        registry.add("app.auth.agent.name", () -> "Test Agent");
        registry.add("app.auth.agent.email", () -> "test-agent@example.com");
    }

    private static Path tempDatabase() {
        try {
            Path file = Files.createTempFile("telecom-security-test", ".db");
            file.toFile().deleteOnExit();
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        // The cookie repository only checks that the header echoes the cookie, as a
        // browser client would send it.
        xsrfCookie = new Cookie(XSRF_COOKIE, UUID.randomUUID().toString());
        usageRepository.deleteAllInBatch();
        ticketRepository.deleteAllInBatch();
        deviceRepository.deleteAllInBatch();
        customerRepository.deleteAllInBatch();
        planRepository.deleteAllInBatch();
        plan = planRepository.save(new Plan("Standard", new BigDecimal("40.00"), 10, 500, 500));
        customer = customerRepository.save(new Customer("Ada", "Lovelace", "ada@example.com", "555-0100", plan));
        ticket = ticketRepository
                .save(new SupportTicket(customer, "Billing", "Question about a bill", SupportTicket.Priority.LOW));
    }

    @Test
    void healthCheckIsPublic() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/health")).andExpect(status().isOk());
    }

    @Test
    void readsWithoutLoginAreUnauthorized() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get(CUSTOMERS)).andExpect(status().isUnauthorized());
        mockMvc.perform(MockMvcRequestBuilders.get("/api/dashboard/stats")).andExpect(status().isUnauthorized());
        mockMvc.perform(MockMvcRequestBuilders.get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void writesWithoutLoginAreUnauthorized() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.delete(CUSTOMER_PATH, customer.getId()).with(xsrf()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(MockMvcRequestBuilders.delete(CUSTOMER_PATH, customer.getId()))
                .andExpect(status().isUnauthorized());

        Assertions.assertTrue(customerRepository.existsById(customer.getId()), "customer should not be deleted");
    }

    @Test
    void loginRejectsAWrongPassword() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post(LOGIN_URL).with(xsrf()).param(USERNAME_PARAM, ADMIN_USERNAME)
                .param(PASSWORD_PARAM, "not-the-password")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid username or password"));
    }

    @Test
    void loginRequiresACsrfToken() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post(LOGIN_URL).param(USERNAME_PARAM, ADMIN_USERNAME)
                .param(PASSWORD_PARAM, ADMIN_PASSWORD)).andExpect(status().isUnauthorized());
    }

    @Test
    void loginReturnsTheProfileAndStartsASession() throws Exception {
        MvcResult result = mockMvc
                .perform(MockMvcRequestBuilders.post(LOGIN_URL).with(xsrf()).param(USERNAME_PARAM, ADMIN_USERNAME)
                        .param(PASSWORD_PARAM, ADMIN_PASSWORD))
                .andExpect(status().isOk()).andExpect(jsonPath("$.username").value(ADMIN_USERNAME))
                .andExpect(jsonPath("$.name").value("Test Admin"))
                .andExpect(jsonPath("$.email").value("test-admin@example.com"))
                .andExpect(jsonPath(ROLE_FIELD).value("ADMIN")).andReturn();
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);

        mockMvc.perform(MockMvcRequestBuilders.get("/api/auth/me").session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Test Admin")).andExpect(jsonPath(ROLE_FIELD).value("ADMIN"));
    }

    @Test
    void browserStyleCsrfCookieFlowLogsIn() throws Exception {
        MvcResult csrfResult = mockMvc.perform(MockMvcRequestBuilders.get("/api/auth/csrf"))
                .andExpect(status().isNoContent()).andReturn();
        Cookie xsrf = csrfResult.getResponse().getCookie(XSRF_COOKIE);
        Assertions.assertTrue(xsrf != null && !xsrf.isHttpOnly(),
                "GET /api/auth/csrf should set an XSRF-TOKEN cookie that the browser client can read");

        mockMvc.perform(MockMvcRequestBuilders.post(LOGIN_URL).cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue())
                .param(USERNAME_PARAM, AGENT_USERNAME).param(PASSWORD_PARAM, AGENT_PASSWORD)).andExpect(status().isOk())
                .andExpect(jsonPath(ROLE_FIELD).value("AGENT"));
    }

    @Test
    void agentCanReadEverySection() throws Exception {
        MockHttpSession session = login(AGENT_USERNAME, AGENT_PASSWORD);

        for (String path : new String[] {CUSTOMERS, "/api/plans", "/api/devices", "/api/usage", TICKETS,
                "/api/dashboard/stats"}) {
            mockMvc.perform(MockMvcRequestBuilders.get(path).session(session)).andExpect(status().isOk());
        }
    }

    @Test
    void agentGetsForbiddenWhenDeletingACustomer() throws Exception {
        MockHttpSession session = login(AGENT_USERNAME, AGENT_PASSWORD);

        mockMvc.perform(MockMvcRequestBuilders.delete(CUSTOMER_PATH, customer.getId()).session(session).with(xsrf()))
                .andExpect(status().isForbidden());

        Assertions.assertTrue(customerRepository.existsById(customer.getId()), "customer should not be deleted");
    }

    @Test
    void agentCannotChangeCustomersPlansOrDevices() throws Exception {
        MockHttpSession session = login(AGENT_USERNAME, AGENT_PASSWORD);
        String customerBody = "{\"firstName\":\"Grace\",\"lastName\":\"Hopper\",\"email\":\"grace@example.com\","
                + "\"phone\":\"555-0101\",\"planId\":" + plan.getId() + "}";

        mockMvc.perform(MockMvcRequestBuilders.post(CUSTOMERS).session(session).with(xsrf())
                .contentType(MediaType.APPLICATION_JSON).content(customerBody)).andExpect(status().isForbidden());
        mockMvc.perform(MockMvcRequestBuilders.put(CUSTOMER_PATH, customer.getId()).session(session).with(xsrf())
                .contentType(MediaType.APPLICATION_JSON).content(customerBody)).andExpect(status().isForbidden());
        mockMvc.perform(MockMvcRequestBuilders.post("/api/plans").session(session).with(xsrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"X\",\"monthlyPrice\":1}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(MockMvcRequestBuilders.delete("/api/plans/{id}", plan.getId()).session(session).with(xsrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(MockMvcRequestBuilders.post("/api/devices").session(session).with(xsrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"imei\":\"1\",\"model\":\"X\",\"simNumber\":\"2\"}")).andExpect(status().isForbidden());
    }

    @Test
    void agentCanCreateAndUpdateTickets() throws Exception {
        MockHttpSession session = login(AGENT_USERNAME, AGENT_PASSWORD);
        String createBody = "{\"customerId\":" + customer.getId()
                + ",\"subject\":\"Roaming\",\"description\":\"Enable roaming\",\"priority\":\"MEDIUM\"}";
        String updateBody = """
                {"subject":"Billing","description":"Answered","status":"RESOLVED"}
                """;

        mockMvc.perform(MockMvcRequestBuilders.post(TICKETS).session(session).with(xsrf())
                .contentType(MediaType.APPLICATION_JSON).content(createBody)).andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("Roaming"));
        mockMvc.perform(MockMvcRequestBuilders.put(TICKET_PATH, ticket.getId()).session(session).with(xsrf())
                .contentType(MediaType.APPLICATION_JSON).content(updateBody)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));
    }

    @Test
    void agentCannotDeleteTickets() throws Exception {
        MockHttpSession session = login(AGENT_USERNAME, AGENT_PASSWORD);

        mockMvc.perform(MockMvcRequestBuilders.delete(TICKET_PATH, ticket.getId()).session(session).with(xsrf()))
                .andExpect(status().isForbidden());

        Assertions.assertTrue(ticketRepository.existsById(ticket.getId()), "ticket should not be deleted");
    }

    @Test
    void adminCanDeleteACustomer() throws Exception {
        Customer spare = customerRepository
                .save(new Customer("Grace", "Hopper", "grace@example.com", "555-0101", plan));
        MockHttpSession session = login(ADMIN_USERNAME, ADMIN_PASSWORD);

        mockMvc.perform(MockMvcRequestBuilders.delete(CUSTOMER_PATH, spare.getId()).session(session).with(xsrf()))
                .andExpect(status().isNoContent());

        Assertions.assertFalse(customerRepository.existsById(spare.getId()), "customer should be deleted");
    }

    @Test
    void adminCanDeleteTickets() throws Exception {
        MockHttpSession session = login(ADMIN_USERNAME, ADMIN_PASSWORD);

        mockMvc.perform(MockMvcRequestBuilders.delete(TICKET_PATH, ticket.getId()).session(session).with(xsrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void loggedInWritesStillNeedACsrfToken() throws Exception {
        MockHttpSession session = login(ADMIN_USERNAME, ADMIN_PASSWORD);

        mockMvc.perform(MockMvcRequestBuilders.delete(CUSTOMER_PATH, customer.getId()).session(session))
                .andExpect(status().isForbidden());

        Assertions.assertTrue(customerRepository.existsById(customer.getId()), "customer should not be deleted");
    }

    @Test
    void logoutEndsTheSession() throws Exception {
        MockHttpSession session = login(ADMIN_USERNAME, ADMIN_PASSWORD);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/logout").session(session).with(xsrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(MockMvcRequestBuilders.get(CUSTOMERS).session(session)).andExpect(status().isUnauthorized());
    }

    /**
     * Sends the CSRF token the way the browser client does: the cookie plus a
     * matching header. (Spring Security's csrf() test helper is avoided because it
     * swaps the token repository for the whole cached context.)
     */
    private RequestPostProcessor xsrf() {
        return request -> {
            request.setCookies(xsrfCookie);
            request.addHeader("X-XSRF-TOKEN", xsrfCookie.getValue());
            return request;
        };
    }

    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    private MockHttpSession login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post(LOGIN_URL).with(xsrf())
                .param(USERNAME_PARAM, username).param(PASSWORD_PARAM, password)).andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}

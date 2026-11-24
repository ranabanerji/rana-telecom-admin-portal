package com.example.telecom.integration;

import com.example.telecom.model.*;
import com.example.telecom.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Exercises real SQLite, the production Flyway schema, controllers and
 * serialization together.
 */
@SpringBootTest
@Transactional
class PortalIntegrationTest {
    @Autowired
    private WebApplicationContext context;
    @Autowired
    private PlanRepository plans;
    @Autowired
    private CustomerRepository customers;
    @Autowired
    private DeviceRepository devices;
    @Autowired
    private SupportTicketRepository tickets;
    @Autowired
    private UsageRecordRepository usage;
    @Autowired
    private JdbcTemplate jdbc;
    private MockMvc mvc;
    private Plan plan;
    private Customer customer;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        plan = plans.saveAndFlush(new Plan("Basic", new BigDecimal("20.00"), 10, 500, 500));
        customer = customers.saveAndFlush(new Customer("Ada", "Lovelace", "ada@example.com", "555", plan));
    }

    @Test
    void migrationsEnableForeignKeysAndUsageOrderingIndex() {
        assertEquals(1, jdbc.queryForObject("PRAGMA foreign_keys", Integer.class));
        assertEquals(1, jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success=1 AND version='1'", Integer.class));
        assertThrows(org.springframework.dao.DataAccessException.class,
                () -> jdbc.update("DELETE FROM plans WHERE id=?", plan.getId()));
        assertThrows(org.springframework.dao.DataAccessException.class, () -> jdbc.update(
                "INSERT INTO usage_records(customer_id,type,quantity,cost,recorded_at) VALUES(-1,'SMS',1,1,1)"));
        String queryPlan = jdbc
                .queryForList(
                        "EXPLAIN QUERY PLAN SELECT * FROM usage_records ORDER BY recorded_at DESC, id DESC LIMIT 50")
                .toString();
        assertTrue(queryPlan.contains("idx_usage_recorded"), queryPlan);
        assertFalse(queryPlan.contains("TEMP B-TREE"), queryPlan);
    }

    @ParameterizedTest
    @ValueSource(strings = {"tickets", "usage", "devices"})
    void dependentCustomerDeleteIsRejectedAndListsStillWork(String dependent) throws Exception {
        if ("tickets".equals(dependent))
            tickets.saveAndFlush(new SupportTicket(customer, "Help", "", SupportTicket.Priority.MEDIUM));
        if ("usage".equals(dependent))
            usage.saveAndFlush(
                    new UsageRecord(customer, UsageRecord.Type.SMS, BigDecimal.ONE, BigDecimal.ONE, Instant.now()));
        if ("devices".equals(dependent)) {
            Device device = new Device("123", "Phone", "sim");
            device.setCustomer(customer);
            device.setStatus(Device.Status.ASSIGNED);
            devices.saveAndFlush(device);
        }
        mvc.perform(delete("/api/customers/" + customer.getId())).andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors.dependencies").exists());
        assertTrue(customers.existsById(customer.getId()));
        mvc.perform(get("/api/tickets")).andExpect(status().isOk());
        mvc.perform(get("/api/usage").param("customerId", customer.getId().toString())).andExpect(status().isOk());
    }

    @Test
    void usedPlanDeleteConflictsWithoutChangingRevenueAndCanBeDeactivated() throws Exception {
        mvc.perform(delete("/api/plans/" + plan.getId())).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Deactivate")));
        mvc.perform(get("/api/dashboard/stats")).andExpect(jsonPath("$.monthly_revenue").value(20));
        mvc.perform(put("/api/plans/" + plan.getId()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Basic\",\"monthlyPrice\":20,\"isActive\":false}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.is_active").value(false));
        mvc.perform(get("/api/dashboard/stats")).andExpect(jsonPath("$.monthly_revenue").value(20));
    }

    @Test
    void independentRowsCanStillBeDeleted() throws Exception {
        mvc.perform(delete("/api/customers/" + customer.getId())).andExpect(status().isNoContent());
        mvc.perform(delete("/api/plans/" + plan.getId())).andExpect(status().isNoContent());
    }

    @ParameterizedTest
    @CsvSource({"/api/tickets,status,BOGUS", "/api/tickets,priority,bogus", "/api/usage,type,bogus",
            "/api/usage,dateFrom,nope", "/api/usage,dateTo,nope"})
    void invalidFiltersNameTheField(String url, String field, String value) throws Exception {
        mvc.perform(get(url).param(field, value)).andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors." + field).exists());
    }

    @ParameterizedTest
    @CsvSource({"/api/customers,{},firstName", "/api/plans,{},monthlyPrice", "/api/tickets,{},priority",
            "/api/devices,{},imei"})
    void missingFieldsAreBadRequests(String url, String body, String field) throws Exception {
        mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors." + field).exists());
    }

    @Test
    void missingAndUnknownReferencesAndDuplicateEmailHaveFieldProblems() throws Exception {
        String base = "{\"firstName\":\"Ada\",\"lastName\":\"Lovelace\",\"email\":\"ada@example.com\",\"phone\":\"555\"";
        mvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON).content(base + "}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.planId").exists());
        mvc.perform(
                post("/api/customers").contentType(MediaType.APPLICATION_JSON).content(base + ",\"planId\":999999}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.planId").exists());
        mvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON)
                .content(base + ",\"planId\":" + plan.getId() + "}")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors.email").exists());
    }

    @Test
    void invalidStatusAndEmailAreBadRequests() throws Exception {
        String body = "{\"firstName\":\"Ada\",\"lastName\":\"Lovelace\",\"email\":\"other@example.com\",\"phone\":\"555\",\"status\":\"bogus\"}";
        mvc.perform(put("/api/customers/" + customer.getId()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.status").exists());
        mvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON)
                .content(body.replace("other@example.com", "invalid"))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists());
    }

    @Test
    void omittedAndClearedTicketDescriptionsStayOptionalAndNewTicketSortsFirst() throws Exception {
        tickets.saveAndFlush(new SupportTicket(customer, "Old ticket", "Details", SupportTicket.Priority.MEDIUM));
        String response = mvc
                .perform(post("/api/tickets").contentType(MediaType.APPLICATION_JSON).content(
                        "{\"customerId\":" + customer.getId() + ",\"subject\":\"Newest\",\"priority\":\"MEDIUM\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.description").value("")).andReturn().getResponse()
                .getContentAsString();
        mvc.perform(get("/api/tickets").param("size", "1")).andExpect(jsonPath("$.content[0].subject").value("Newest"));
        SupportTicket ticket = tickets.findAll().stream().filter(t -> "Old ticket".equals(t.getSubject())).findFirst()
                .orElseThrow();
        mvc.perform(put("/api/tickets/" + ticket.getId()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"subject\":\"Old ticket\",\"priority\":\"MEDIUM\",\"description\":\"\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.description").value(""));
        assertEquals("integer", jdbc
                .queryForObject("SELECT typeof(created_at) FROM support_tickets WHERE subject='Newest'", String.class));
        assertTrue(response.contains("Z"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"OPEN", "IN_PROGRESS"})
    void reopeningClearsResolutionAndTerminalTransitionsPreserveIt(String reopened) throws Exception {
        SupportTicket ticket = tickets
                .saveAndFlush(new SupportTicket(customer, "Help", "", SupportTicket.Priority.MEDIUM));
        updateTicket(ticket, "RESOLVED");
        Instant first = ticket.getResolvedAt();
        assertNotNull(first);
        updateTicket(ticket, "CLOSED");
        assertEquals(first, ticket.getResolvedAt());
        updateTicket(ticket, reopened);
        assertNull(ticket.getResolvedAt());
        updateTicket(ticket, "CLOSED");
        assertNotNull(ticket.getResolvedAt());
        assertFalse(ticket.getResolvedAt().isBefore(first));
    }

    private void updateTicket(SupportTicket ticket, String status) throws Exception {
        mvc.perform(put("/api/tickets/" + ticket.getId()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"subject\":\"Help\",\"priority\":\"MEDIUM\",\"status\":\"" + status + "\"}"))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"AVAILABLE", "ASSIGNED", "LOST", "DAMAGED"})
    void everyDeviceStatusTransitionHonorsAssignment(String initial) throws Exception {
        Device device = new Device("123", "Phone", "sim");
        device.setStatus(Device.Status.valueOf(initial));
        if ("ASSIGNED".equals(initial)) {
            device.setCustomer(customer);
            device.setAssignedAt(Instant.now());
        }
        devices.saveAndFlush(device);
        for (String next : List.of("ASSIGNED", "AVAILABLE", "LOST", "DAMAGED")) {
            device.setStatus(Device.Status.valueOf(initial));
            device.setCustomer("ASSIGNED".equals(initial) ? customer : null);
            device.setAssignedAt("ASSIGNED".equals(initial) ? Instant.now() : null);
            devices.flush();
            mvc.perform(put("/api/devices/" + device.getId()).contentType(MediaType.APPLICATION_JSON)
                    .content(deviceBody(next, customer.getId()))).andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(next)).andExpect(jsonPath("$.customer_id")
                            .value("ASSIGNED".equals(next) ? customer.getId().intValue() : null));
            if ("ASSIGNED".equals(next))
                assertNotNull(device.getAssignedAt());
            else
                assertNull(device.getAssignedAt());
            mvc.perform(get("/api/dashboard/stats"))
                    .andExpect(jsonPath("$.devices_in_use").value("ASSIGNED".equals(next) ? 1 : 0));
        }
    }

    @Test
    void deviceCreateAndUpdateRejectUnknownCustomerAndUnassignedAssignedStatus() throws Exception {
        Device device = devices.saveAndFlush(new Device("123", "Phone", "sim"));
        for (String url : List.of("/api/devices", "/api/devices/" + device.getId())) {
            var request = url.equals("/api/devices") ? post(url) : put(url);
            mvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(deviceBody("ASSIGNED", 999999L)))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.customerId").exists());
            var missing = url.equals("/api/devices") ? post(url) : put(url);
            mvc.perform(missing.contentType(MediaType.APPLICATION_JSON).content(deviceBody("ASSIGNED", null)))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.customerId").exists());
        }
        mvc.perform(put("/api/devices/" + device.getId()).contentType(MediaType.APPLICATION_JSON)
                .content(deviceBody("bogus", null))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.status").exists());
    }

    private static String deviceBody(String status, Long customerId) {
        return "{\"imei\":\"123\",\"model\":\"Phone\",\"simNumber\":\"sim\",\"customerId\":" + customerId
                + ",\"status\":\"" + status + "\"}";
    }

    @Test
    void duplicatePlanNamesHaveIndependentCountsAndRevenue() throws Exception {
        Plan second = plans.saveAndFlush(new Plan("Basic", new BigDecimal("35.00"), 5, 50, 50));
        customers.saveAndFlush(new Customer("Grace", "Hopper", "grace@example.com", "555", second));
        mvc.perform(get("/api/dashboard/revenue-by-plan")).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].plan_id").value(plan.getId().intValue()))
                .andExpect(jsonPath("$[0].revenue").value(20))
                .andExpect(jsonPath("$[1].plan_id").value(second.getId().intValue()))
                .andExpect(jsonPath("$[1].revenue").value(35));
        mvc.perform(get("/api/dashboard/customers-by-plan")).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].value").value(1)).andExpect(jsonPath("$[1].value").value(1));
    }

    @Test
    void usageUtcFiltersMatchSqlAndExcludeNextDayMidnight() throws Exception {
        for (String instant : List.of("2026-09-29T23:59:59.999Z", "2026-09-30T00:00:00Z", "2026-09-30T23:59:59.999Z",
                "2026-10-01T00:00:00Z")) {
            // Same epoch representation used by the Python seed and by JPA.
            jdbc.update("INSERT INTO usage_records(customer_id,type,quantity,cost,recorded_at) VALUES(?,'SMS',1,1,?)",
                    customer.getId(), Instant.parse(instant).toEpochMilli());
        }
        usage.saveAndFlush(new UsageRecord(customer, UsageRecord.Type.CALL, BigDecimal.ONE, BigDecimal.ONE,
                Instant.parse("2026-09-30T12:34:56.123Z")));
        assertEquals(5, jdbc.queryForObject("SELECT count(*) FROM usage_records WHERE typeof(recorded_at)='integer'",
                Integer.class));
        mvc.perform(get("/api/usage").param("dateFrom", "2026-09-30").param("dateTo", "2026-09-30"))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].recorded_at").value("2026-09-30T23:59:59.999Z"));
        mvc.perform(get("/api/usage").param("dateTo", "2026-09-30")).andExpect(jsonPath("$.totalElements").value(4));
        mvc.perform(get("/api/usage").param("dateFrom", "2026-09-30")).andExpect(jsonPath("$.totalElements").value(4));
        mvc.perform(get("/api/usage")).andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.content[0].recorded_at").value("2026-10-01T00:00:00Z"));
        assertEquals(3,
                jdbc.queryForObject("SELECT count(*) FROM usage_records WHERE recorded_at>=? AND recorded_at<?",
                        Integer.class, Instant.parse("2026-09-30T00:00:00Z").toEpochMilli(),
                        Instant.parse("2026-10-01T00:00:00Z").toEpochMilli()));
    }

    @Test
    void aggregateDateBucketsUseEpochMillisecondsInUtc() {
        customer.setCreatedAt(Instant.parse("2026-09-30T23:59:59.999Z"));
        customers.saveAndFlush(customer);
        assertEquals("2026-09", customers.countByMonth().getFirst()[0]);
        usage.saveAndFlush(new UsageRecord(customer, UsageRecord.Type.SMS, BigDecimal.ONE, BigDecimal.ONE,
                Instant.parse("2026-09-30T23:59:59.999Z")));
        usage.saveAndFlush(new UsageRecord(customer, UsageRecord.Type.SMS, BigDecimal.ONE, BigDecimal.ONE,
                Instant.parse("2026-10-01T00:00:00Z")));
        List<Object[]> buckets = usage.sumByTypeAndDate(Instant.parse("2026-10-01T00:00:00Z"));
        assertEquals(1, buckets.size());
        assertEquals("2026-10-01", buckets.getFirst()[1]);
    }

    @Test
    void boundedListsAreOrderedAndAdvertiseTheirRealTotals() throws Exception {
        for (int i = 0; i < 1200; i++)
            jdbc.update(
                    "INSERT INTO customers(first_name,last_name,email,phone,plan_id,status,balance,created_at) VALUES('Test','User',?,'555',?,'ACTIVE',0,0)",
                    "test" + i + "@example.com", plan.getId());
        for (int i = 0; i < 105; i++)
            plans.save(new Plan("Extra", BigDecimal.ONE, 1, 1, 1));
        plans.flush();
        mvc.perform(get("/api/customers")).andExpect(header().string("X-Total-Count", "1201"))
                .andExpect(jsonPath("$.length()").value(1000))
                .andExpect(jsonPath("$[0].id").value(customer.getId().intValue()));
        mvc.perform(get("/api/plans")).andExpect(header().string("X-Total-Count", "106"))
                .andExpect(jsonPath("$.length()").value(100))
                .andExpect(jsonPath("$[0].id").value(plan.getId().intValue()));
        for (int i = 0; i < 1001; i++) {
            jdbc.update(
                    "INSERT INTO devices(imei,model,sim_number,status,created_at) VALUES(?,'Phone',?,'AVAILABLE',0)",
                    "imei" + i, "sim" + i);
            jdbc.update("INSERT INTO usage_records(customer_id,type,quantity,cost,recorded_at) VALUES(?,'SMS',1,1,0)",
                    customer.getId());
        }
        mvc.perform(get("/api/devices")).andExpect(header().string("X-Total-Count", "1001"))
                .andExpect(jsonPath("$.length()").value(1000));
        mvc.perform(get("/api/usage/customer/" + customer.getId())).andExpect(header().string("X-Total-Count", "1001"))
                .andExpect(jsonPath("$.length()").value(1000));
    }
}

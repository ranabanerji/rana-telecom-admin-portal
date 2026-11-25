package com.example.telecom;

import com.example.telecom.model.Customer;
import com.example.telecom.model.Device;
import com.example.telecom.model.Plan;
import com.example.telecom.model.SupportTicket;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

/** Detached entities with IDs for unit tests that do not touch a database. */
public final class EntityFixtures {

    public static final Instant NOW = Instant.parse("2026-11-25T10:00:00Z");
    public static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private EntityFixtures() {
    }

    public static Plan plan(Long id, String name, String price) {
        Plan plan = new Plan(name, new BigDecimal(price), 10, 500, 500);
        plan.setId(id);
        return plan;
    }

    public static Customer customer(Long id, Plan plan) {
        Customer customer = new Customer("First" + id, "Last" + id, "customer" + id + "@example.com", "555-000" + id,
                plan);
        customer.setId(id);
        return customer;
    }

    public static Device device(Long id) {
        Device device = new Device("imei" + id, "Phone", "sim" + id);
        device.setId(id);
        return device;
    }

    public static SupportTicket ticket(Long id, Customer customer) {
        SupportTicket ticket = new SupportTicket(customer, "Help", "Details", SupportTicket.Priority.MEDIUM);
        ticket.setId(id);
        return ticket;
    }
}

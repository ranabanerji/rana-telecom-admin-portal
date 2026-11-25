package com.example.telecom.integration;

import com.example.telecom.model.Customer;
import com.example.telecom.model.Plan;
import com.example.telecom.repository.CustomerRepository;
import com.example.telecom.repository.PlanRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Loads entities with {@link Instant} columns from many threads at once against
 * real SQLite. Not transactional, so every pooled connection sees the rows.
 */
@SpringBootTest
class ConcurrentTimestampReadIntegrationTest {
    private static final int CUSTOMERS = 200;
    private static final int THREADS = 16;
    private static final int READS = 400;
    private static final Instant CREATED = Instant.parse("2026-03-14T15:09:26.535Z");

    @Autowired
    private CustomerRepository customers;
    @Autowired
    private PlanRepository plans;
    private Plan plan;
    private List<Customer> saved;

    @BeforeEach
    void setUp() {
        plan = plans.saveAndFlush(new Plan("Concurrent reads", new BigDecimal("10.00"), 1, 1, 1));
        saved = customers.saveAllAndFlush(IntStream.range(0, CUSTOMERS).mapToObj(i -> {
            Customer customer = new Customer("Load", "Test" + i, "load" + i + "@example.com", "555", plan);
            customer.setCreatedAt(CREATED.plusSeconds(i));
            return customer;
        }).toList());
    }

    @AfterEach
    void tearDown() {
        customers.deleteAllInBatch(saved);
        plans.delete(plan);
    }

    @Test
    void parallelReadsReturnTheStoredInstants() throws Exception {
        List<Long> ids = saved.stream().map(Customer::getId).toList();
        List<Instant> expected = IntStream.range(0, CUSTOMERS).mapToObj(CREATED::plusSeconds).toList();
        try (ExecutorService pool = Executors.newFixedThreadPool(THREADS)) {
            List<Future<List<Instant>>> reads = new ArrayList<>();
            for (int i = 0; i < READS; i++) {
                reads.add(pool.submit(
                        () -> customers.findAllById(ids).stream().map(Customer::getCreatedAt).sorted().toList()));
            }
            for (Future<List<Instant>> read : reads) {
                assertEquals(expected, read.get(30, TimeUnit.SECONDS));
            }
        }
    }
}

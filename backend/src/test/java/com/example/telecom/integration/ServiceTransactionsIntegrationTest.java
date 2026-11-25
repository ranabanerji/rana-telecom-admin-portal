package com.example.telecom.integration;

import com.example.telecom.service.CustomerService;
import com.example.telecom.service.DeviceService;
import com.example.telecom.service.PlanService;
import com.example.telecom.service.TicketService;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionAttribute;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every public service method runs in a Spring-managed transaction: writes
 * read-write, reads read-only.
 */
@SpringBootTest
class ServiceTransactionsIntegrationTest {

    private static final Set<String> WRITES = Set.of("create", "update", "delete");

    @Autowired
    private CustomerService customerService;
    @Autowired
    private PlanService planService;
    @Autowired
    private DeviceService deviceService;
    @Autowired
    private TicketService ticketService;

    @Test
    void publicServiceMethodsAreTransactional() {
        AnnotationTransactionAttributeSource attributes = new AnnotationTransactionAttributeSource();
        for (Object service : List.of(customerService, planService, deviceService, ticketService)) {
            assertTrue(AopUtils.isAopProxy(service), service.getClass() + " should be proxied");
            Class<?> type = AopUtils.getTargetClass(service);
            for (Method method : type.getDeclaredMethods()) {
                if (!Modifier.isPublic(method.getModifiers())) {
                    continue;
                }
                TransactionAttribute attribute = attributes.getTransactionAttribute(method, type);
                String name = type.getSimpleName() + "." + method.getName();
                assertNotNull(attribute, name + " should be transactional");
                assertEquals(!WRITES.contains(method.getName()), attribute.isReadOnly(),
                        name + " should be " + (WRITES.contains(method.getName()) ? "read-write" : "read-only"));
            }
        }
    }
}

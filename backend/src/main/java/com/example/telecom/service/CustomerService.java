package com.example.telecom.service;

import com.example.telecom.model.Customer;
import com.example.telecom.model.Plan;
import com.example.telecom.repository.CustomerRepository;
import com.example.telecom.repository.DeviceRepository;
import com.example.telecom.repository.PlanRepository;
import com.example.telecom.repository.SupportTicketRepository;
import com.example.telecom.repository.UsageRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Customer rules:
 * <ul>
 * <li>a customer always has an existing plan;</li>
 * <li>new customers start ACTIVE; status and plan change only when given;</li>
 * <li>a customer with tickets, usage or devices cannot be deleted.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class CustomerService {

    private static final Logger LOG = LoggerFactory.getLogger(CustomerService.class);
    private static final String PLAN_ID = "planId";

    private final CustomerRepository customers;
    private final PlanRepository plans;
    private final SupportTicketRepository tickets;
    private final UsageRecordRepository usage;
    private final DeviceRepository devices;

    public CustomerService(CustomerRepository customers, PlanRepository plans, SupportTicketRepository tickets,
            UsageRecordRepository usage, DeviceRepository devices) {
        this.customers = customers;
        this.plans = plans;
        this.tickets = tickets;
        this.usage = usage;
        this.devices = devices;
    }

    public Page<Customer> findPage(Pageable pageable) {
        return customers.findAll(pageable);
    }

    public Customer get(Long id) {
        return customers.findById(id).orElseThrow(() -> new NotFoundException("Customer", id));
    }

    @Transactional
    public Customer create(CustomerDetails details) {
        Plan plan = requirePlan(details.planId());
        Customer customer = new Customer(details.firstName(), details.lastName(), details.email(), details.phone(),
                plan);
        if (details.balance() != null) {
            customer.setBalance(details.balance());
        }
        Customer saved = customers.save(customer);
        LOG.info("Customer created: id={}", saved.getId());
        return saved;
    }

    @Transactional
    public Customer update(Long id, CustomerDetails details) {
        Customer customer = get(id);
        Plan plan = details.planId() == null ? customer.getPlan() : requirePlan(details.planId());
        customer.setFirstName(details.firstName());
        customer.setLastName(details.lastName());
        customer.setEmail(details.email());
        customer.setPhone(details.phone());
        customer.setPlan(plan);
        if (details.status() != null) {
            customer.setStatus(details.status());
        }
        if (details.balance() != null) {
            customer.setBalance(details.balance());
        }
        Customer saved = customers.save(customer);
        LOG.info("Customer updated: id={}, status={}", saved.getId(), saved.getStatus());
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        if (!customers.existsById(id)) {
            throw new NotFoundException("Customer", id);
        }
        if (tickets.existsByCustomerId(id) || usage.existsByCustomerId(id) || devices.existsByCustomerId(id)) {
            throw new ConflictException("dependencies",
                    "Customer has tickets, usage or devices. Reassign dependents or deactivate the customer instead.");
        }
        customers.deleteById(id);
        LOG.info("Customer deleted: id={}", id);
    }

    private Plan requirePlan(Long planId) {
        if (planId == null) {
            throw new InvalidInputException(PLAN_ID, "planId is required");
        }
        return plans.findById(planId)
                .orElseThrow(() -> new InvalidInputException(PLAN_ID, "planId must identify an existing plan"));
    }

    /**
     * Customer fields from a create or update request. {@code status} is ignored on
     * create; {@code planId}, {@code status} and {@code balance} are left unchanged
     * on update when null.
     */
    public record CustomerDetails(String firstName, String lastName, String email, String phone, Long planId,
            Customer.Status status, BigDecimal balance) {
    }
}

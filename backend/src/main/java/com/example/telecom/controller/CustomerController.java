package com.example.telecom.controller;

import com.example.telecom.model.Customer;
import com.example.telecom.model.Plan;
import com.example.telecom.repository.CustomerRepository;
import com.example.telecom.repository.PlanRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.example.telecom.repository.SupportTicketRepository;
import com.example.telecom.repository.UsageRecordRepository;
import com.example.telecom.repository.DeviceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private static final Logger LOG = LoggerFactory.getLogger(CustomerController.class);

    private final CustomerRepository repository;
    private final PlanRepository planRepository;
    private final SupportTicketRepository tickets;
    private final UsageRecordRepository usage;
    private final DeviceRepository devices;

    public CustomerController(CustomerRepository repository, PlanRepository planRepository,
            SupportTicketRepository tickets, UsageRecordRepository usage, DeviceRepository devices) {
        this.repository = repository;
        this.planRepository = planRepository;
        this.tickets = tickets;
        this.usage = usage;
        this.devices = devices;
    }

    @GetMapping
    public ResponseEntity<List<CustomerResponse>> getAllCustomers() {
        LOG.info("Fetching all customers");
        List<CustomerResponse> customers = repository.findAll(PageRequest.of(0, 1000, Sort.by("id"))).stream()
                .map(CustomerResponse::from).toList();
        LOG.debug("Found {} customers", customers.size());
        return ResponseEntity.ok().header("X-Total-Count", Long.toString(repository.count())).body(customers);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CustomerResponse> getCustomer(@PathVariable Long id) {
        LOG.info("Fetching customer with id={}", id);
        return repository.findById(id).map(customer -> {
            LOG.debug("Found customer: id={}", customer.getId());
            return ResponseEntity.ok(CustomerResponse.from(customer));
        }).orElseGet(() -> {
            LOG.warn("Customer not found: id={}", id);
            return ResponseEntity.notFound().build();
        });
    }

    @PostMapping
    public ResponseEntity<CustomerResponse> createCustomer(@Valid @RequestBody CustomerRequest request) {
        LOG.info("Creating customer");
        if (request.planId() == null) {
            throw new FieldProblem("planId", "planId is required");
        }
        Inputs.enumValue(Customer.Status.class, request.status(), "status");
        Plan plan = planRepository.findById(request.planId()).orElse(null);
        if (plan == null) {
            LOG.warn("Plan not found for customer creation: planId={}", request.planId());
            throw new FieldProblem("planId", "planId must identify an existing plan");
        }
        Customer customer = new Customer(request.firstName(), request.lastName(), request.email(), request.phone(),
                plan);
        if (request.balance() != null) {
            customer.setBalance(request.balance());
        }
        Customer saved = repository.save(customer);
        LOG.info("Customer created: id={}", saved.getId());
        return ResponseEntity.ok(CustomerResponse.from(saved));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CustomerResponse> updateCustomer(@PathVariable Long id,
            @Valid @RequestBody CustomerRequest request) {
        LOG.info("Updating customer: id={}", id);
        return repository.findById(id).map(customer -> {
            customer.setFirstName(request.firstName());
            customer.setLastName(request.lastName());
            customer.setEmail(request.email());
            customer.setPhone(request.phone());
            if (request.planId() != null) {
                customer.setPlan(planRepository.findById(request.planId())
                        .orElseThrow(() -> new FieldProblem("planId", "Unknown planId")));
            }
            if (request.status() != null) {
                customer.setStatus(Inputs.enumValue(Customer.Status.class, request.status(), "status"));
            }
            if (request.balance() != null) {
                customer.setBalance(request.balance());
            }
            Customer saved = repository.save(customer);
            LOG.info("Customer updated: id={}", saved.getId());
            return ResponseEntity.ok(CustomerResponse.from(saved));
        }).orElseGet(() -> {
            LOG.warn("Customer not found for update: id={}", id);
            return ResponseEntity.notFound().build();
        });
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCustomer(@PathVariable Long id) {
        LOG.info("Deleting customer: id={}", id);
        if (!repository.existsById(id)) {
            LOG.warn("Customer not found for deletion: id={}", id);
            return ResponseEntity.notFound().build();
        }
        if (tickets.existsByCustomerId(id) || usage.existsByCustomerId(id) || devices.existsByCustomerId(id)) {
            throw new FieldProblem("dependencies",
                    "Customer has tickets, usage or devices. Reassign dependents or deactivate the customer instead.",
                    HttpStatus.CONFLICT);
        }
        repository.deleteById(id);
        LOG.info("Customer deleted: id={}", id);
        return ResponseEntity.noContent().build();
    }

    public record CustomerRequest(@NotBlank String firstName, @NotBlank String lastName, @NotBlank @Email String email,
            @NotBlank String phone, @Positive Long planId, String status, BigDecimal balance) {
    }

    public record CustomerResponse(Long id, String first_name, String last_name, String email, String phone,
            Long plan_id, String plan_name, String status, BigDecimal balance, String activated_at, String created_at) {
        public static CustomerResponse from(Customer customer) {
            return new CustomerResponse(customer.getId(), customer.getFirstName(), customer.getLastName(),
                    customer.getEmail(), customer.getPhone(),
                    customer.getPlan() != null ? customer.getPlan().getId() : null,
                    customer.getPlan() != null ? customer.getPlan().getName() : null, customer.getStatus().name(),
                    customer.getBalance(),
                    customer.getActivatedAt() != null ? customer.getActivatedAt().toString() : null,
                    customer.getCreatedAt() != null ? customer.getCreatedAt().toString() : null);
        }
    }
}

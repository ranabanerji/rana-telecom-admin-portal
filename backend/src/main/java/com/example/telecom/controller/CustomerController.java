package com.example.telecom.controller;

import com.example.telecom.model.Customer;
import com.example.telecom.service.CustomerService;
import com.example.telecom.service.CustomerService.CustomerDetails;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private static final int LIST_LIMIT = 1000;

    private final CustomerService service;

    public CustomerController(CustomerService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<CustomerResponse>> getAllCustomers() {
        Page<Customer> page = service.findPage(PageRequest.of(0, LIST_LIMIT, Sort.by("id")));
        return ResponseEntity.ok().header("X-Total-Count", Long.toString(page.getTotalElements()))
                .body(page.map(CustomerResponse::from).getContent());
    }

    @GetMapping("/{id}")
    public CustomerResponse getCustomer(@PathVariable Long id) {
        return CustomerResponse.from(service.get(id));
    }

    @PostMapping
    public CustomerResponse createCustomer(@Valid @RequestBody CustomerRequest request) {
        return CustomerResponse.from(service.create(request.toDetails()));
    }

    @PutMapping("/{id}")
    public CustomerResponse updateCustomer(@PathVariable Long id, @Valid @RequestBody CustomerRequest request) {
        return CustomerResponse.from(service.update(id, request.toDetails()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCustomer(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    public record CustomerRequest(@NotBlank String firstName, @NotBlank String lastName, @NotBlank @Email String email,
            @NotBlank String phone, @Positive Long planId, String status, BigDecimal balance) {
        CustomerDetails toDetails() {
            return new CustomerDetails(firstName, lastName, email, phone, planId,
                    Inputs.enumValue(Customer.Status.class, status, "status"), balance);
        }
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

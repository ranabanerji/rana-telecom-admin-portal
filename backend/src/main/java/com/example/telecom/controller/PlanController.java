package com.example.telecom.controller;

import com.example.telecom.model.Plan;
import com.example.telecom.repository.PlanRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.example.telecom.repository.CustomerRepository;
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
@RequestMapping("/api/plans")
public class PlanController {

    private static final Logger LOG = LoggerFactory.getLogger(PlanController.class);

    private final PlanRepository repository;
    private final CustomerRepository customers;

    public PlanController(PlanRepository repository, CustomerRepository customers) {
        this.repository = repository;
        this.customers = customers;
    }

    @GetMapping
    public ResponseEntity<List<PlanResponse>> getAllPlans() {
        LOG.info("Fetching all plans");
        List<PlanResponse> plans = repository.findAll(PageRequest.of(0, 100, Sort.by("id"))).stream()
                .map(PlanResponse::from).toList();
        LOG.debug("Found {} plans", plans.size());
        return ResponseEntity.ok().header("X-Total-Count", Long.toString(repository.count())).body(plans);
    }

    @GetMapping("/active")
    public List<PlanResponse> getActivePlans() {
        LOG.info("Fetching active plans");
        List<PlanResponse> plans = repository.findByIsActiveTrue().stream().map(PlanResponse::from).toList();
        LOG.debug("Found {} active plans", plans.size());
        return plans;
    }

    @GetMapping("/{id}")
    public ResponseEntity<PlanResponse> getPlan(@PathVariable Long id) {
        LOG.info("Fetching plan with id={}", id);
        return repository.findById(id).map(plan -> {
            LOG.debug("Found plan: id={}", plan.getId());
            return ResponseEntity.ok(PlanResponse.from(plan));
        }).orElseGet(() -> {
            LOG.warn("Plan not found: id={}", id);
            return ResponseEntity.notFound().build();
        });
    }

    @PostMapping
    public PlanResponse createPlan(@Valid @RequestBody PlanRequest request) {
        LOG.info("Creating plan");
        Plan plan = new Plan(request.name(), request.monthlyPrice(), request.dataLimitGb(), request.minutesLimit(),
                request.smsLimit());
        Plan saved = repository.save(plan);
        LOG.info("Plan created: id={}", saved.getId());
        return PlanResponse.from(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<PlanResponse> updatePlan(@PathVariable Long id, @Valid @RequestBody PlanRequest request) {
        LOG.info("Updating plan: id={}", id);
        return repository.findById(id).map(plan -> {
            plan.setName(request.name());
            plan.setMonthlyPrice(request.monthlyPrice());
            plan.setDataLimitGb(request.dataLimitGb());
            plan.setMinutesLimit(request.minutesLimit());
            plan.setSmsLimit(request.smsLimit());
            if (request.isActive() != null) {
                plan.setIsActive(request.isActive());
            }
            Plan saved = repository.save(plan);
            LOG.info("Plan updated: id={}", saved.getId());
            return ResponseEntity.ok(PlanResponse.from(saved));
        }).orElseGet(() -> {
            LOG.warn("Plan not found for update: id={}", id);
            return ResponseEntity.notFound().build();
        });
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePlan(@PathVariable Long id) {
        LOG.info("Deleting plan: id={}", id);
        if (!repository.existsById(id)) {
            LOG.warn("Plan not found for deletion: id={}", id);
            return ResponseEntity.notFound().build();
        }
        if (customers.existsByPlanId(id)) {
            throw new FieldProblem("planId", "Customers use this plan. Deactivate it instead of deleting it.",
                    HttpStatus.CONFLICT);
        }
        repository.deleteById(id);
        LOG.info("Plan deleted: id={}", id);
        return ResponseEntity.noContent().build();
    }

    public record PlanRequest(@NotBlank @Size(max = FieldLimits.NAME, message = FieldLimits.TOO_LONG) String name,
            @NotNull @Positive @Digits(integer = FieldLimits.MONEY_INTEGER_DIGITS, fraction = FieldLimits.MONEY_FRACTION_DIGITS, message = FieldLimits.MONEY) BigDecimal monthlyPrice,
            @PositiveOrZero Integer dataLimitGb, @PositiveOrZero Integer minutesLimit, @PositiveOrZero Integer smsLimit,
            Boolean isActive) {
    }

    public record PlanResponse(Long id, String name, BigDecimal monthly_price, Integer data_limit_gb,
            Integer minutes_limit, Integer sms_limit, Boolean is_active) {
        public static PlanResponse from(Plan plan) {
            return new PlanResponse(plan.getId(), plan.getName(), plan.getMonthlyPrice(), plan.getDataLimitGb(),
                    plan.getMinutesLimit(), plan.getSmsLimit(), plan.getIsActive());
        }
    }
}

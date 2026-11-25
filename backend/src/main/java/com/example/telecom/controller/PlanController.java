package com.example.telecom.controller;

import com.example.telecom.model.Plan;
import com.example.telecom.service.PlanService;
import com.example.telecom.service.PlanService.PlanDetails;
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
@RequestMapping("/api/plans")
public class PlanController {

    private static final int LIST_LIMIT = 100;

    private final PlanService service;

    public PlanController(PlanService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<PlanResponse>> getAllPlans() {
        Page<Plan> page = service.findPage(PageRequest.of(0, LIST_LIMIT, Sort.by("id")));
        return ResponseEntity.ok().header("X-Total-Count", Long.toString(page.getTotalElements()))
                .body(page.map(PlanResponse::from).getContent());
    }

    @GetMapping("/active")
    public List<PlanResponse> getActivePlans() {
        return service.findActive().stream().map(PlanResponse::from).toList();
    }

    @GetMapping("/{id}")
    public PlanResponse getPlan(@PathVariable Long id) {
        return PlanResponse.from(service.get(id));
    }

    @PostMapping
    public PlanResponse createPlan(@Valid @RequestBody PlanRequest request) {
        return PlanResponse.from(service.create(request.toDetails()));
    }

    @PutMapping("/{id}")
    public PlanResponse updatePlan(@PathVariable Long id, @Valid @RequestBody PlanRequest request) {
        return PlanResponse.from(service.update(id, request.toDetails()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePlan(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    public record PlanRequest(@NotBlank String name, @NotNull @Positive BigDecimal monthlyPrice,
            @PositiveOrZero Integer dataLimitGb, @PositiveOrZero Integer minutesLimit, @PositiveOrZero Integer smsLimit,
            Boolean isActive) {
        PlanDetails toDetails() {
            return new PlanDetails(name, monthlyPrice, dataLimitGb, minutesLimit, smsLimit, isActive);
        }
    }

    public record PlanResponse(Long id, String name, BigDecimal monthly_price, Integer data_limit_gb,
            Integer minutes_limit, Integer sms_limit, Boolean is_active) {
        public static PlanResponse from(Plan plan) {
            return new PlanResponse(plan.getId(), plan.getName(), plan.getMonthlyPrice(), plan.getDataLimitGb(),
                    plan.getMinutesLimit(), plan.getSmsLimit(), plan.getIsActive());
        }
    }
}

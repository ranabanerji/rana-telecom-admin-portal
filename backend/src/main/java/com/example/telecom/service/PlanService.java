package com.example.telecom.service;

import com.example.telecom.model.Plan;
import com.example.telecom.repository.CustomerRepository;
import com.example.telecom.repository.PlanRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Plan rules: new plans start active, the active flag changes only when given,
 * and a plan that customers use cannot be deleted (deactivate it instead).
 */
@Service
@Transactional(readOnly = true)
public class PlanService {

    private static final Logger LOG = LoggerFactory.getLogger(PlanService.class);

    private final PlanRepository plans;
    private final CustomerRepository customers;

    public PlanService(PlanRepository plans, CustomerRepository customers) {
        this.plans = plans;
        this.customers = customers;
    }

    public Page<Plan> findPage(Pageable pageable) {
        return plans.findAll(pageable);
    }

    public List<Plan> findActive() {
        return plans.findByIsActiveTrue();
    }

    public Plan get(Long id) {
        return plans.findById(id).orElseThrow(() -> new NotFoundException("Plan", id));
    }

    @Transactional
    public Plan create(PlanDetails details) {
        Plan saved = plans.save(new Plan(details.name(), details.monthlyPrice(), details.dataLimitGb(),
                details.minutesLimit(), details.smsLimit()));
        LOG.info("Plan created: id={}", saved.getId());
        return saved;
    }

    @Transactional
    public Plan update(Long id, PlanDetails details) {
        Plan plan = get(id);
        plan.setName(details.name());
        plan.setMonthlyPrice(details.monthlyPrice());
        plan.setDataLimitGb(details.dataLimitGb());
        plan.setMinutesLimit(details.minutesLimit());
        plan.setSmsLimit(details.smsLimit());
        if (details.isActive() != null) {
            plan.setIsActive(details.isActive());
        }
        Plan saved = plans.save(plan);
        LOG.info("Plan updated: id={}, active={}", saved.getId(), saved.getIsActive());
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        if (!plans.existsById(id)) {
            throw new NotFoundException("Plan", id);
        }
        if (customers.existsByPlanId(id)) {
            throw new ConflictException("planId", "Customers use this plan. Deactivate it instead of deleting it.");
        }
        plans.deleteById(id);
        LOG.info("Plan deleted: id={}", id);
    }

    /**
     * Plan fields from a create or update request. {@code isActive} is ignored on
     * create and left unchanged on update when null.
     */
    public record PlanDetails(String name, BigDecimal monthlyPrice, Integer dataLimitGb, Integer minutesLimit,
            Integer smsLimit, Boolean isActive) {
    }
}

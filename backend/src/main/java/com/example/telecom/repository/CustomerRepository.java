package com.example.telecom.repository;

import com.example.telecom.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    boolean existsByPlanId(Long planId);

    long countByStatus(Customer.Status status);

    /**
     * Active subscribers grouped by plan: plan id, display name, monthly price and
     * subscriber count. Keyed by plan id so duplicate plan names stay separate.
     */
    @Query("SELECT c.plan.id, c.plan.name, c.plan.monthlyPrice, COUNT(c) FROM Customer c WHERE c.status = 'ACTIVE' GROUP BY c.plan.id, c.plan.name, c.plan.monthlyPrice ORDER BY c.plan.id")
    List<Object[]> aggregateActiveByPlan();
}

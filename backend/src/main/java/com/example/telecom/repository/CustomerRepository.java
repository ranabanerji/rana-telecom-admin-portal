package com.example.telecom.repository;

import com.example.telecom.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    boolean existsByPlanId(Long planId);

    List<Customer> findByStatus(Customer.Status status);

    long countByStatus(Customer.Status status);

    @Query("SELECT c.plan.id, c.plan.name, COUNT(c) FROM Customer c WHERE c.status = 'ACTIVE' GROUP BY c.plan.id, c.plan.name ORDER BY c.plan.id")
    List<Object[]> countByPlanId();

    @Query(value = "SELECT strftime('%Y-%m', created_at / 1000.0, 'unixepoch'), COUNT(*) FROM customers GROUP BY 1 ORDER BY 1 DESC", nativeQuery = true)
    List<Object[]> countByMonth();
}

package com.example.telecom.repository;

import com.example.telecom.model.UsageRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface UsageRecordRepository extends JpaRepository<UsageRecord, Long> {
    boolean existsByCustomerId(Long customerId);

    long countByCustomerId(Long customerId);

    Page<UsageRecord> findByCustomerId(Long customerId, Pageable pageable);

    Page<UsageRecord> findAllByOrderByRecordedAtDescIdDesc(Pageable pageable);

    @Query(value = "SELECT type, date(recorded_at / 1000.0, 'unixepoch'), SUM(quantity) FROM usage_records WHERE recorded_at >= :since GROUP BY 1, 2 ORDER BY 2, 1", nativeQuery = true)
    List<Object[]> sumByTypeAndDate(@Param("since") Instant since);

    @Query("SELECT u FROM UsageRecord u WHERE " + "(:type IS NULL OR u.type = :type) AND "
            + "(:customerId IS NULL OR u.customer.id = :customerId) AND "
            + "(:dateFrom IS NULL OR u.recordedAt >= :dateFrom) AND " + "(:dateTo IS NULL OR u.recordedAt < :dateTo) "
            + "ORDER BY u.recordedAt DESC, u.id DESC")
    Page<UsageRecord> findFiltered(@Param("type") UsageRecord.Type type, @Param("customerId") Long customerId,
            @Param("dateFrom") Instant dateFrom, @Param("dateTo") Instant dateTo, Pageable pageable);
}

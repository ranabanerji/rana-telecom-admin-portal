package com.example.telecom.controller;

import com.example.telecom.model.UsageRecord;
import com.example.telecom.repository.UsageRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/usage")
public class UsageRecordController {

    private static final Logger LOG = LoggerFactory.getLogger(UsageRecordController.class);

    private final UsageRecordRepository repository;

    public UsageRecordController(UsageRecordRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public PagedResponse getUsageRecords(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size, @RequestParam(required = false) String type,
            @RequestParam(required = false) Long customerId, @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo) {
        LOG.info("Fetching usage records");
        UsageRecord.Type typeEnum = Inputs.enumValue(UsageRecord.Type.class, type, "type");
        Instant from = Inputs.date(dateFrom, "dateFrom", false);
        Instant to = Inputs.date(dateTo, "dateTo", true);
        if (from != null && to != null && !from.isBefore(to)) {
            throw new FieldProblem("dateTo", "dateTo must be on or after dateFrom");
        }

        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), Math.max(1, Math.min(size, 100)));
        Page<UsageRecord> result;

        boolean hasFilters = typeEnum != null || customerId != null || from != null || to != null;
        if (hasFilters) {
            result = repository.findFiltered(typeEnum, customerId, from, to, pageRequest);
        } else {
            result = repository.findAllByOrderByRecordedAtDescIdDesc(pageRequest);
        }

        LOG.debug("Found {} usage records (page {} of {})", result.getNumberOfElements(), result.getNumber(),
                result.getTotalPages());
        return new PagedResponse(result.getContent().stream().map(UsageRecordResponse::from).toList(),
                result.getNumber(), result.getTotalPages(), result.getTotalElements(), result.hasNext());
    }

    @GetMapping("/customer/{customerId}")
    public ResponseEntity<List<UsageRecordResponse>> getByCustomer(@PathVariable Long customerId) {
        LOG.info("Fetching usage records for customer: customerId={}", customerId);
        List<UsageRecordResponse> records = repository
                .findByCustomerId(customerId, PageRequest.of(0, 1000, Sort.by("id"))).stream()
                .map(UsageRecordResponse::from).toList();
        LOG.debug("Found {} usage records for customer {}", records.size(), customerId);
        return ResponseEntity.ok().header("X-Total-Count", Long.toString(repository.countByCustomerId(customerId)))
                .body(records);
    }

    public record PagedResponse(List<UsageRecordResponse> content, int page, int totalPages, long totalElements,
            boolean hasNext) {
    }

    public record UsageRecordResponse(Long id, Long customer_id, String customer_name, String type, BigDecimal quantity,
            BigDecimal cost, String recorded_at) {
        public static UsageRecordResponse from(UsageRecord record) {
            return new UsageRecordResponse(record.getId(), record.getCustomer().getId(),
                    record.getCustomer().getFirstName() + " " + record.getCustomer().getLastName(),
                    record.getType().name(), record.getQuantity(), record.getCost(),
                    record.getRecordedAt() != null ? record.getRecordedAt().toString() : null);
        }
    }
}

package com.example.telecom.controller;

import com.example.telecom.model.Device;
import com.example.telecom.model.Customer;
import com.example.telecom.repository.CustomerRepository;
import com.example.telecom.repository.DeviceRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/devices")
public class DeviceController {

    private static final Logger LOG = LoggerFactory.getLogger(DeviceController.class);

    private final DeviceRepository repository;
    private final CustomerRepository customerRepository;

    public DeviceController(DeviceRepository repository, CustomerRepository customerRepository) {
        this.repository = repository;
        this.customerRepository = customerRepository;
    }

    @GetMapping
    public ResponseEntity<List<DeviceResponse>> getAllDevices() {
        LOG.info("Fetching all devices");
        List<DeviceResponse> devices = repository.findAll(PageRequest.of(0, 1000, Sort.by("id"))).stream()
                .map(DeviceResponse::from).toList();
        LOG.debug("Found {} devices", devices.size());
        return ResponseEntity.ok().header("X-Total-Count", Long.toString(repository.count())).body(devices);
    }

    @GetMapping("/{id}")
    public ResponseEntity<DeviceResponse> getDevice(@PathVariable Long id) {
        LOG.info("Fetching device with id={}", id);
        return repository.findById(id).map(device -> {
            LOG.debug("Found device: id={}", device.getId());
            return ResponseEntity.ok(DeviceResponse.from(device));
        }).orElseGet(() -> {
            LOG.warn("Device not found: id={}", id);
            return ResponseEntity.notFound().build();
        });
    }

    @PostMapping
    public DeviceResponse createDevice(@Valid @RequestBody DeviceRequest request) {
        LOG.info("Creating device");
        Device device = new Device(request.imei(), request.model(), request.simNumber());
        applyAssignment(device, request);
        Device saved = repository.save(device);
        LOG.info("Device created: id={}", saved.getId());
        return DeviceResponse.from(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<DeviceResponse> updateDevice(@PathVariable Long id,
            @Valid @RequestBody DeviceRequest request) {
        LOG.info("Updating device: id={}", id);
        return repository.findById(id).map(device -> {
            device.setImei(request.imei());
            device.setModel(request.model());
            device.setSimNumber(request.simNumber());
            applyAssignment(device, request);
            Device saved = repository.save(device);
            LOG.info("Device updated: id={}", saved.getId());
            return ResponseEntity.ok(DeviceResponse.from(saved));
        }).orElseGet(() -> {
            LOG.warn("Device not found for update: id={}", id);
            return ResponseEntity.notFound().build();
        });
    }

    private void applyAssignment(Device device, DeviceRequest request) {
        Device.Status status = Inputs.enumValue(Device.Status.class, request.status(), "status");
        Customer customer = request.customerId() == null
                ? null
                : customerRepository.findById(request.customerId())
                        .orElseThrow(() -> new FieldProblem("customerId", "Unknown customerId"));
        if (status == null) {
            status = customer == null ? Device.Status.AVAILABLE : Device.Status.ASSIGNED;
        }
        if (status == Device.Status.ASSIGNED && customer == null) {
            throw new FieldProblem("customerId", "ASSIGNED requires an existing customerId");
        }
        if (status == Device.Status.ASSIGNED) {
            assignCustomer(device, customer);
        } else {
            device.setCustomer(null);
            device.setAssignedAt(null);
        }
        device.setStatus(status);
    }

    private static void assignCustomer(Device device, Customer customer) {
        if (device.getCustomer() == null || !device.getCustomer().getId().equals(customer.getId())) {
            device.setAssignedAt(Instant.now());
        }
        device.setCustomer(customer);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDevice(@PathVariable Long id) {
        LOG.info("Deleting device: id={}", id);
        if (!repository.existsById(id)) {
            LOG.warn("Device not found for deletion: id={}", id);
            return ResponseEntity.notFound().build();
        }
        repository.deleteById(id);
        LOG.info("Device deleted: id={}", id);
        return ResponseEntity.noContent().build();
    }

    public record DeviceRequest(@NotBlank String imei, @NotBlank String model, @NotBlank String simNumber,
            @Positive Long customerId, String status) {
    }

    public record DeviceResponse(Long id, String imei, String model, String sim_number, Long customer_id,
            String customer_name, String status, String assigned_at, String created_at) {
        public static DeviceResponse from(Device device) {
            return new DeviceResponse(device.getId(), device.getImei(), device.getModel(), device.getSimNumber(),
                    device.getCustomer() != null ? device.getCustomer().getId() : null,
                    device.getCustomer() != null
                            ? device.getCustomer().getFirstName() + " " + device.getCustomer().getLastName()
                            : null,
                    device.getStatus().name(),
                    device.getAssignedAt() != null ? device.getAssignedAt().toString() : null,
                    device.getCreatedAt() != null ? device.getCreatedAt().toString() : null);
        }
    }
}

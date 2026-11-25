package com.example.telecom.controller;

import com.example.telecom.model.Device;
import com.example.telecom.service.DeviceService;
import com.example.telecom.service.DeviceService.DeviceDetails;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/devices")
public class DeviceController {

    private static final int LIST_LIMIT = 1000;

    private final DeviceService service;

    public DeviceController(DeviceService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<DeviceResponse>> getAllDevices() {
        Page<Device> page = service.findPage(PageRequest.of(0, LIST_LIMIT, Sort.by("id")));
        return ResponseEntity.ok().header("X-Total-Count", Long.toString(page.getTotalElements()))
                .body(page.map(DeviceResponse::from).getContent());
    }

    @GetMapping("/{id}")
    public DeviceResponse getDevice(@PathVariable Long id) {
        return DeviceResponse.from(service.get(id));
    }

    @PostMapping
    public DeviceResponse createDevice(@Valid @RequestBody DeviceRequest request) {
        return DeviceResponse.from(service.create(request.toDetails()));
    }

    @PutMapping("/{id}")
    public DeviceResponse updateDevice(@PathVariable Long id, @Valid @RequestBody DeviceRequest request) {
        return DeviceResponse.from(service.update(id, request.toDetails()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDevice(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    public record DeviceRequest(@NotBlank String imei, @NotBlank String model, @NotBlank String simNumber,
            @Positive Long customerId, String status) {
        DeviceDetails toDetails() {
            return new DeviceDetails(imei, model, simNumber, customerId,
                    Inputs.enumValue(Device.Status.class, status, "status"));
        }
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

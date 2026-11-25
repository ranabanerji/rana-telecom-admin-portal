package com.example.telecom.service;

import com.example.telecom.model.Customer;
import com.example.telecom.model.Device;
import com.example.telecom.repository.CustomerRepository;
import com.example.telecom.repository.DeviceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * Device assignment rules, enforced only here:
 * <ul>
 * <li>a given {@code customerId} must identify an existing customer, whatever
 * the status;</li>
 * <li>without a status, a device with a customer becomes ASSIGNED and one
 * without becomes AVAILABLE;</li>
 * <li>ASSIGNED requires a customer. {@code assignedAt} is set when the device
 * moves to a new customer and kept while it stays with the same one;</li>
 * <li>AVAILABLE, LOST and DAMAGED release the device: the customer and
 * {@code assignedAt} are cleared, so only ASSIGNED devices count as in
 * use.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class DeviceService {

    private static final Logger LOG = LoggerFactory.getLogger(DeviceService.class);
    private static final String CUSTOMER_ID = "customerId";

    private final DeviceRepository devices;
    private final CustomerRepository customers;
    private final Clock clock;

    public DeviceService(DeviceRepository devices, CustomerRepository customers, Clock clock) {
        this.devices = devices;
        this.customers = customers;
        this.clock = clock;
    }

    public Page<Device> findPage(Pageable pageable) {
        return devices.findAll(pageable);
    }

    public Device get(Long id) {
        return devices.findById(id).orElseThrow(() -> new NotFoundException("Device", id));
    }

    @Transactional
    public Device create(DeviceDetails details) {
        Assignment assignment = resolveAssignment(details);
        Device device = new Device(details.imei(), details.model(), details.simNumber());
        assign(device, assignment);
        Device saved = devices.save(device);
        LOG.info("Device created: id={}, status={}", saved.getId(), saved.getStatus());
        return saved;
    }

    @Transactional
    public Device update(Long id, DeviceDetails details) {
        Device device = get(id);
        Assignment assignment = resolveAssignment(details);
        device.setImei(details.imei());
        device.setModel(details.model());
        device.setSimNumber(details.simNumber());
        assign(device, assignment);
        Device saved = devices.save(device);
        LOG.info("Device updated: id={}, status={}", saved.getId(), saved.getStatus());
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        if (!devices.existsById(id)) {
            throw new NotFoundException("Device", id);
        }
        devices.deleteById(id);
        LOG.info("Device deleted: id={}", id);
    }

    private Assignment resolveAssignment(DeviceDetails details) {
        Customer customer = details.customerId() == null
                ? null
                : customers.findById(details.customerId())
                        .orElseThrow(() -> new InvalidInputException(CUSTOMER_ID, "Unknown customerId"));
        Device.Status status = details.status();
        if (status == null) {
            status = customer == null ? Device.Status.AVAILABLE : Device.Status.ASSIGNED;
        }
        if (status != Device.Status.ASSIGNED) {
            return new Assignment(status, null);
        }
        if (customer == null) {
            throw new InvalidInputException(CUSTOMER_ID, "ASSIGNED requires an existing customerId");
        }
        return new Assignment(status, customer);
    }

    private void assign(Device device, Assignment assignment) {
        Customer customer = assignment.customer();
        if (customer == null) {
            device.setCustomer(null);
            device.setAssignedAt(null);
        } else if (!isAssignedTo(device, customer)) {
            device.setCustomer(customer);
            device.setAssignedAt(clock.instant());
        }
        device.setStatus(assignment.status());
    }

    private static boolean isAssignedTo(Device device, Customer customer) {
        return device.getStatus() == Device.Status.ASSIGNED && device.getCustomer() != null
                && device.getAssignedAt() != null && device.getCustomer().getId().equals(customer.getId());
    }

    /** Device fields from a create or update request. */
    public record DeviceDetails(String imei, String model, String simNumber, Long customerId, Device.Status status) {
    }

    /**
     * The validated outcome of the assignment rules: a customer only for ASSIGNED.
     */
    private record Assignment(Device.Status status, Customer customer) {
    }
}

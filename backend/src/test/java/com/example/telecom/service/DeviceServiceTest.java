package com.example.telecom.service;

import com.example.telecom.model.Customer;
import com.example.telecom.model.Device;
import com.example.telecom.repository.CustomerRepository;
import com.example.telecom.repository.DeviceRepository;
import com.example.telecom.service.DeviceService.DeviceDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Stream;

import static com.example.telecom.EntityFixtures.CLOCK;
import static com.example.telecom.EntityFixtures.NOW;
import static com.example.telecom.EntityFixtures.customer;
import static com.example.telecom.EntityFixtures.device;
import static com.example.telecom.EntityFixtures.plan;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeviceServiceTest {

    private static final Instant EARLIER = Instant.parse("2026-01-01T00:00:00Z");

    private final DeviceRepository devices = mock(DeviceRepository.class);
    private final CustomerRepository customers = mock(CustomerRepository.class);
    private final DeviceService service = new DeviceService(devices, customers, CLOCK);

    private Customer ada;
    private Customer grace;

    @BeforeEach
    void setUp() {
        ada = customer(1L, plan(1L, "Basic", "20.00"));
        grace = customer(2L, plan(1L, "Basic", "20.00"));
        when(customers.findById(1L)).thenReturn(Optional.of(ada));
        when(customers.findById(2L)).thenReturn(Optional.of(grace));
        when(devices.save(any(Device.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    static Stream<Arguments> everyStatusChange() {
        return Arrays.stream(Device.Status.values())
                .flatMap(from -> Arrays.stream(Device.Status.values()).map(to -> Arguments.of(from, to)));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("everyStatusChange")
    void everyStatusChangeAppliesTheAssignmentRules(Device.Status from, Device.Status to) {
        Device device = existing(from);

        Device updated = service.update(7L, details(1L, to));

        assertEquals(to, updated.getStatus());
        if (to == Device.Status.ASSIGNED) {
            assertSame(ada, updated.getCustomer());
            assertEquals(from == Device.Status.ASSIGNED ? EARLIER : NOW, updated.getAssignedAt(),
                    "assignedAt changes only when the device moves to a new customer");
        } else {
            assertNull(updated.getCustomer(), "only ASSIGNED devices keep a customer");
            assertNull(updated.getAssignedAt());
        }
        assertSame(device, updated);
    }

    @Test
    void reassigningToAnotherCustomerRestartsTheAssignment() {
        existing(Device.Status.ASSIGNED);

        Device updated = service.update(7L, details(2L, Device.Status.ASSIGNED));

        assertSame(grace, updated.getCustomer());
        assertEquals(NOW, updated.getAssignedAt());
    }

    @Test
    void assignedRequiresACustomer() {
        Device device = existing(Device.Status.AVAILABLE);

        InvalidInputException error = assertThrows(InvalidInputException.class,
                () -> service.update(7L, details(null, Device.Status.ASSIGNED)));

        assertEquals("customerId", error.getField());
        assertEquals(Device.Status.AVAILABLE, device.getStatus());
        verify(devices, never()).save(any(Device.class));
    }

    @ParameterizedTest
    @EnumSource(Device.Status.class)
    void anUnknownCustomerIsRejectedWhateverTheStatus(Device.Status status) {
        when(customers.findById(99L)).thenReturn(Optional.empty());

        InvalidInputException error = assertThrows(InvalidInputException.class,
                () -> service.create(details(99L, status)));

        assertEquals("customerId", error.getField());
        verify(devices, never()).save(any(Device.class));
    }

    @Test
    void withoutAStatusACustomerMeansAssigned() {
        Device created = service.create(details(1L, null));

        assertEquals(Device.Status.ASSIGNED, created.getStatus());
        assertSame(ada, created.getCustomer());
        assertEquals(NOW, created.getAssignedAt());
    }

    @Test
    void withoutAStatusOrCustomerTheDeviceIsAvailable() {
        Device created = service.create(details(null, null));

        assertEquals(Device.Status.AVAILABLE, created.getStatus());
        assertNull(created.getCustomer());
        assertNull(created.getAssignedAt());
    }

    @Test
    void anUnassignedStatusIgnoresTheGivenCustomer() {
        Device created = service.create(details(1L, Device.Status.AVAILABLE));

        assertEquals(Device.Status.AVAILABLE, created.getStatus());
        assertNull(created.getCustomer());
    }

    @Test
    void updateCopiesTheHardwareFields() {
        existing(Device.Status.AVAILABLE);

        Device updated = service.update(7L, new DeviceDetails("new-imei", "Tablet", "new-sim", null, null));

        assertEquals("new-imei", updated.getImei());
        assertEquals("Tablet", updated.getModel());
        assertEquals("new-sim", updated.getSimNumber());
    }

    @Test
    void getUpdateAndDeleteReportAMissingDevice() {
        when(devices.findById(anyLong())).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.get(404L));
        assertThrows(NotFoundException.class, () -> service.update(404L, details(null, null)));
        assertThrows(NotFoundException.class, () -> service.delete(404L));
        verify(devices, never()).deleteById(anyLong());
    }

    @Test
    void deleteRemovesAnExistingDevice() {
        when(devices.existsById(7L)).thenReturn(true);

        service.delete(7L);

        verify(devices).deleteById(7L);
    }

    private Device existing(Device.Status status) {
        Device device = device(7L);
        device.setStatus(status);
        if (status == Device.Status.ASSIGNED) {
            device.setCustomer(ada);
            device.setAssignedAt(EARLIER);
        }
        when(devices.findById(7L)).thenReturn(Optional.of(device));
        return device;
    }

    private static DeviceDetails details(Long customerId, Device.Status status) {
        return new DeviceDetails("imei7", "Phone", "sim7", customerId, status);
    }
}

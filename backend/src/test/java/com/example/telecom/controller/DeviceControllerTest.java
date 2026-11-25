package com.example.telecom.controller;

import com.example.telecom.model.Device;
import com.example.telecom.service.DeviceService;
import com.example.telecom.service.DeviceService.DeviceDetails;
import com.example.telecom.service.InvalidInputException;
import com.example.telecom.service.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static com.example.telecom.EntityFixtures.customer;
import static com.example.telecom.EntityFixtures.device;
import static com.example.telecom.EntityFixtures.plan;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HTTP mapping only; the assignment rules are covered by DeviceServiceTest. */
class DeviceControllerTest {

    private static final String BODY = """
            {"imei":"123","model":"Phone","simNumber":"sim","customerId":1,"status":"ASSIGNED"}
            """;
    private static final DeviceDetails DETAILS = new DeviceDetails("123", "Phone", "sim", 1L, Device.Status.ASSIGNED);

    private final DeviceService service = mock(DeviceService.class);

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new DeviceController(service))
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }

    @Test
    void listReturnsTheFirstPageWithTheTotalCount() throws Exception {
        PageRequest firstPage = PageRequest.of(0, 1000, Sort.by("id"));
        when(service.findPage(firstPage)).thenReturn(new PageImpl<>(List.of(device(7L)), firstPage, 1001));

        mockMvc.perform(get("/api/devices")).andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "1001")).andExpect(jsonPath("$[0].id").value(7))
                .andExpect(jsonPath("$[0].sim_number").value("sim7"))
                .andExpect(jsonPath("$[0].status").value("AVAILABLE"))
                .andExpect(jsonPath("$[0].customer_id").value(nullValue()));
    }

    @Test
    void createPassesTheParsedRequestToTheService() throws Exception {
        when(service.create(DETAILS)).thenReturn(assigned());

        mockMvc.perform(post("/api/devices").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.customer_id").value(1))
                .andExpect(jsonPath("$.customer_name").value("First1 Last1"))
                .andExpect(jsonPath("$.assigned_at").value("2026-11-25T10:00:00Z"));
    }

    @Test
    void updatePassesTheIdAndParsedRequestToTheService() throws Exception {
        when(service.update(7L, DETAILS)).thenReturn(assigned());

        mockMvc.perform(put("/api/devices/7").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(7));
    }

    @Test
    void anUnknownStatusIsRejectedBeforeCallingTheService() throws Exception {
        mockMvc.perform(put("/api/devices/7").contentType(MediaType.APPLICATION_JSON)
                .content(BODY.replace("ASSIGNED", "bogus"))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.status").exists());

        verify(service, never()).update(anyLong(), any());
    }

    @Test
    void assignmentRuleViolationsAreBadRequests() throws Exception {
        when(service.update(anyLong(), any())).thenThrow(new InvalidInputException("customerId", "Unknown customerId"));

        mockMvc.perform(put("/api/devices/7").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.customerId").value("Unknown customerId"));
    }

    @Test
    void getAndUpdateReturnNotFoundForAnUnknownId() throws Exception {
        when(service.get(anyLong())).thenThrow(new NotFoundException("Device", 404L));
        when(service.update(anyLong(), any())).thenThrow(new NotFoundException("Device", 404L));

        mockMvc.perform(get("/api/devices/404")).andExpect(status().isNotFound());
        mockMvc.perform(put("/api/devices/404").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteReturnsNoContentOrNotFound() throws Exception {
        doThrow(new NotFoundException("Device", 404L)).when(service).delete(404L);

        mockMvc.perform(delete("/api/devices/7")).andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/devices/404")).andExpect(status().isNotFound());

        verify(service).delete(7L);
    }

    private static Device assigned() {
        Device device = device(7L);
        device.setStatus(Device.Status.ASSIGNED);
        device.setCustomer(customer(1L, plan(1L, "Basic", "20.00")));
        device.setAssignedAt(Instant.parse("2026-11-25T10:00:00Z"));
        return device;
    }
}

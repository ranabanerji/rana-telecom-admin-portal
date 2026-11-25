package com.example.telecom.controller;

import com.example.telecom.model.SupportTicket;
import com.example.telecom.model.SupportTicket.Priority;
import com.example.telecom.model.SupportTicket.Status;
import com.example.telecom.service.InvalidInputException;
import com.example.telecom.service.NotFoundException;
import com.example.telecom.service.TicketService;
import com.example.telecom.service.TicketService.TicketDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static com.example.telecom.EntityFixtures.customer;
import static com.example.telecom.EntityFixtures.plan;
import static com.example.telecom.EntityFixtures.ticket;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP mapping only; the ticket status rules are covered by TicketServiceTest.
 */
class SupportTicketControllerTest {

    private static final String BODY = """
            {"customerId":1,"subject":"Roaming","description":"Enable roaming","priority":"HIGH","status":"RESOLVED"}
            """;
    private static final TicketDetails DETAILS = new TicketDetails(1L, "Roaming", "Enable roaming", Priority.HIGH,
            Status.RESOLVED);

    private final TicketService service = mock(TicketService.class);

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new SupportTicketController(service))
                .setControllerAdvice(new ApiExceptionHandler()).build();
        when(service.search(any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of()));
    }

    @Test
    void listWithoutParametersAsksForTheFirstPageOfFifty() throws Exception {
        PageRequest firstPage = PageRequest.of(0, 50);
        when(service.search(null, null, null, firstPage))
                .thenReturn(new PageImpl<>(List.of(resolved()), firstPage, 51));

        mockMvc.perform(get("/api/tickets")).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].customer_name").value("First1 Last1"))
                .andExpect(jsonPath("$.content[0].status").value("RESOLVED"))
                .andExpect(jsonPath("$.content[0].resolved_at").value("2026-11-25T10:00:00Z"))
                .andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.totalElements").value(51)).andExpect(jsonPath("$.hasNext").value(true));
    }

    @Test
    void listPassesParsedFiltersToTheService() throws Exception {
        mockMvc.perform(get("/api/tickets").param("priority", "HIGH").param("status", "OPEN").param("customerId", "3")
                .param("page", "2").param("size", "10")).andExpect(status().isOk());

        verify(service).search(Priority.HIGH, Status.OPEN, 3L, PageRequest.of(2, 10));
    }

    @ParameterizedTest
    @CsvSource({"-1,50,0,50", "0,0,0,1", "0,500,0,100"})
    void listKeepsPagingWithinBounds(int page, int size, int expectedPage, int expectedSize) throws Exception {
        mockMvc.perform(get("/api/tickets").param("page", Integer.toString(page)).param("size", Integer.toString(size)))
                .andExpect(status().isOk());

        verify(service).search(null, null, null, PageRequest.of(expectedPage, expectedSize));
    }

    @Test
    void listRejectsAnUnknownFilterValue() throws Exception {
        mockMvc.perform(get("/api/tickets").param("priority", "bogus")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.priority").exists());

        verify(service, never()).search(any(), any(), any(), any());
    }

    @Test
    void createPassesTheParsedRequestToTheService() throws Exception {
        when(service.create(DETAILS)).thenReturn(resolved());

        mockMvc.perform(post("/api/tickets").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.customer_id").value(1));
    }

    @Test
    void createReportsAnUnknownCustomerAgainstTheField() throws Exception {
        when(service.create(any())).thenThrow(new InvalidInputException("customerId", "Unknown customerId"));

        mockMvc.perform(post("/api/tickets").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.customerId").exists());
    }

    @Test
    void updatePassesTheIdAndParsedRequestToTheService() throws Exception {
        when(service.update(5L, DETAILS)).thenReturn(resolved());

        mockMvc.perform(put("/api/tickets/5").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk()).andExpect(jsonPath("$.resolved_at").value("2026-11-25T10:00:00Z"));
    }

    @Test
    void updateRejectsAnUnknownStatusBeforeCallingTheService() throws Exception {
        mockMvc.perform(put("/api/tickets/5").contentType(MediaType.APPLICATION_JSON)
                .content(BODY.replace("RESOLVED", "bogus"))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.status").exists());

        verify(service, never()).update(anyLong(), any());
    }

    @Test
    void getUpdateAndDeleteReturnNotFoundForAnUnknownId() throws Exception {
        when(service.get(anyLong())).thenThrow(new NotFoundException("Ticket", 404L));
        when(service.update(anyLong(), any())).thenThrow(new NotFoundException("Ticket", 404L));
        doThrow(new NotFoundException("Ticket", 404L)).when(service).delete(404L);

        mockMvc.perform(get("/api/tickets/404")).andExpect(status().isNotFound());
        mockMvc.perform(put("/api/tickets/404").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/tickets/404")).andExpect(status().isNotFound());
    }

    @Test
    void deleteReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/tickets/5")).andExpect(status().isNoContent());

        verify(service).delete(5L);
    }

    private static SupportTicket resolved() {
        SupportTicket ticket = ticket(5L, customer(1L, plan(1L, "Basic", "20.00")));
        ticket.setStatus(Status.RESOLVED);
        ticket.setResolvedAt(Instant.parse("2026-11-25T10:00:00Z"));
        return ticket;
    }
}

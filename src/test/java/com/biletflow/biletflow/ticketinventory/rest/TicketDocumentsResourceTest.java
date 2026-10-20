package com.biletflow.biletflow.ticketinventory.rest;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.biletflow.biletflow.common.application.AuthorizationException;
import com.biletflow.biletflow.ticketinventory.application.delivery.TicketDeliveryCooldownException;
import com.biletflow.biletflow.ticketinventory.application.delivery.TicketDocumentService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class TicketDocumentsResourceTest {

    @Test
    void pdfUsesPrivateNonCacheableAttachmentResponse() throws Exception {
        var documents = mock(TicketDocumentService.class);
        var id = UUID.randomUUID();
        when(documents.pdf(id)).thenReturn(new byte[] { 1, 2, 3 });
        var mvc = MockMvcBuilders.standaloneSetup(new TicketDocumentsResource(documents)).build();
        mvc.perform(get("/api/tickets/{id}/pdf", id).accept(MediaType.APPLICATION_PDF))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_PDF))
            .andExpect(header().string("Content-Disposition", "attachment; filename=\"ticket-" + id + ".pdf\""))
            .andExpect(header().string("Cache-Control", "no-store, private"))
            .andExpect(content().bytes(new byte[] { 1, 2, 3 }));
    }

    @Test
    void pdfAcceptHeaderDoesNotHideAnAuthorizationFailure() throws Exception {
        var documents = mock(TicketDocumentService.class);
        var id = UUID.randomUUID();
        when(documents.pdf(id)).thenThrow(new AuthorizationException("Ticket is private"));
        var mvc = MockMvcBuilders.standaloneSetup(new TicketDocumentsResource(documents))
            .setControllerAdvice(new TicketInventoryExceptionHandler())
            .build();
        mvc.perform(get("/api/tickets/{id}/pdf", id).accept(MediaType.APPLICATION_PDF))
            .andExpect(status().isForbidden())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void resendCooldownReturnsAConflict() throws Exception {
        var documents = mock(TicketDocumentService.class);
        var id = UUID.randomUUID();
        doThrow(new TicketDeliveryCooldownException()).when(documents).requestEmail(id);
        var mvc = MockMvcBuilders.standaloneSetup(new TicketDocumentsResource(documents))
            .setControllerAdvice(new TicketInventoryExceptionHandler())
            .build();
        mvc.perform(post("/api/tickets/{id}/delivery", id))
            .andExpect(status().isConflict())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.detail").value("Wait at least one minute before requesting another email"));
    }
}

package com.biletflow.biletflow.ordercheckout.rest;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.biletflow.biletflow.common.rest.ApiProblems;
import com.biletflow.biletflow.common.security.CurrentActor;
import com.biletflow.biletflow.iam.rest.errors.ErrorConstants;
import com.biletflow.biletflow.iam.rest.errors.ExceptionTranslator;
import com.biletflow.biletflow.ordercheckout.application.CheckoutService;
import com.biletflow.biletflow.paymentsmonetization.rest.PaymentExceptionAdvice;
import com.biletflow.biletflow.ticketinventory.ohs.CheckoutInventoryOhs;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CheckoutErrorContractTest {

    private final CheckoutService checkout = mock(CheckoutService.class);
    private final CurrentActor actor = mock(CurrentActor.class);

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new CheckoutResource(checkout, actor))
        .setControllerAdvice(new CheckoutExceptionAdvice(), new PaymentExceptionAdvice(), new ExceptionTranslator())
        .build();

    @Test
    void invalidCheckoutReturnsEveryFieldError() throws Exception {
        mvc.perform(
            post("/api/checkouts").header("Idempotency-Key", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content("{}")
        )
            .andExpect(status().isBadRequest())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.type").value(ErrorConstants.CONSTRAINT_VIOLATION_TYPE.toString()))
            .andExpect(jsonPath("$.fieldErrors[*].field", containsInAnyOrder("eventId", "selections")))
            .andExpect(jsonPath("$.fieldErrors[*].code", containsInAnyOrder("NotNull", "NotNull")));
    }

    @Test
    void unavailableSeatUsesSpecificProblemInsteadOfGenericConflict() throws Exception {
        UUID seatId = UUID.randomUUID();
        when(actor.requireUserId()).thenReturn(42L);
        when(checkout.create(any(), eq(42L), any())).thenThrow(
            new CheckoutInventoryOhs.Unavailable("Seat is unavailable", seatId, new IllegalStateException())
        );

        String request = """
        {
          "eventId": "%s",
          "selections": [
            {
              "ticketTypeId": "%s",
              "seats": ["%s"],
              "attendeeEmails": ["attendee@example.com"]
            }
          ]
        }
        """.formatted(UUID.randomUUID(), UUID.randomUUID(), seatId);

        mvc.perform(
            post("/api/checkouts").header("Idempotency-Key", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content(request)
        )
            .andExpect(status().isConflict())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.type").value(ApiProblems.type("inventory-unavailable").toString()))
            .andExpect(jsonPath("$.unavailableSeatIds[0]").value(seatId.toString()))
            .andExpect(jsonPath("$.fieldErrors").doesNotExist());
    }
}

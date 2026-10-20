package com.biletflow.biletflow.paymentsmonetization.rest;

import static org.junit.jupiter.api.Assertions.*;

import com.biletflow.biletflow.common.application.AuthorizationException;
import com.biletflow.biletflow.common.security.UnauthenticatedActorException;
import com.biletflow.biletflow.paymentsmonetization.application.PaymentProviderUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class PaymentExceptionAdviceTest {

    @Test
    void authenticationAndAuthorizationUseExistingProblemResponseConventions() {
        var advice = new PaymentExceptionAdvice();
        var unauthenticated = advice.handleUnauthenticated(new UnauthenticatedActorException("Login required"));
        var forbidden = advice.handleForbidden(new AuthorizationException("Organizer permission required"));
        assertEquals(HttpStatus.UNAUTHORIZED, unauthenticated.getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, forbidden.getStatusCode());
        assertEquals("about:blank", forbidden.getBody().getType().toString());
        assertEquals("Forbidden", forbidden.getBody().getTitle());
    }

    @Test
    void providerFailureIsMappedThroughTheApplicationPortException() {
        var response = new PaymentExceptionAdvice().handleUnavailable(new PaymentProviderUnavailableException());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("Payment provider unavailable", response.getBody().getTitle());
    }
}

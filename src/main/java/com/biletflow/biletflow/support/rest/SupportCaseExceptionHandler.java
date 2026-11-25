package com.biletflow.biletflow.support.rest;

import com.biletflow.biletflow.common.application.AuthorizationException;
import com.biletflow.biletflow.common.application.EntityNotFoundException;
import com.biletflow.biletflow.common.rest.ApiProblems;
import com.biletflow.biletflow.common.security.UnauthenticatedActorException;
import com.biletflow.biletflow.support.domain.exceptions.SupportCaseDomainException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = { SupportCaseResource.class, SupportSocketTicketResource.class, SupportResolutionResource.class })
public class SupportCaseExceptionHandler {

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(EntityNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Support case not found", exception.getMessage());
    }

    @ExceptionHandler(UnauthenticatedActorException.class)
    public ResponseEntity<ProblemDetail> handleUnauthenticated(UnauthenticatedActorException exception) {
        return problem(HttpStatus.UNAUTHORIZED, "Authentication required", exception.getMessage());
    }

    @ExceptionHandler(AuthorizationException.class)
    public ResponseEntity<ProblemDetail> handleForbidden(AuthorizationException exception) {
        return problem(HttpStatus.FORBIDDEN, "Forbidden", exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleBadRequest(IllegalArgumentException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid support case request", exception.getMessage());
    }

    @ExceptionHandler(com.biletflow.biletflow.ticketinventory.application.delivery.TicketDeliveryCooldownException.class)
    public ResponseEntity<ProblemDetail> handleCooldown(RuntimeException exception) {
        return problem(HttpStatus.TOO_MANY_REQUESTS, "Ticket email cooldown", exception.getMessage());
    }

    @ExceptionHandler(com.biletflow.biletflow.paymentsmonetization.application.PaymentProviderUnavailableException.class)
    public ResponseEntity<ProblemDetail> handlePaymentUnavailable(RuntimeException exception) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Payment provider unavailable", exception.getMessage());
    }

    @ExceptionHandler(com.biletflow.biletflow.eventmanagement.domain.exceptions.SocialEventNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleEventNotFound(RuntimeException exception) {
        return problem(HttpStatus.NOT_FOUND, "Event not found", exception.getMessage());
    }

    @ExceptionHandler({ SupportCaseDomainException.class, IllegalStateException.class,
        com.biletflow.biletflow.paymentsmonetization.domain.exceptions.PaymentsDomainException.class })
    public ResponseEntity<ProblemDetail> handleConflict(RuntimeException exception) {
        return problem(HttpStatus.CONFLICT, "Support case operation rejected", exception.getMessage());
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String title, String detail) {
        return ApiProblems.response(ApiProblems.body(status, null, title, detail));
    }
}


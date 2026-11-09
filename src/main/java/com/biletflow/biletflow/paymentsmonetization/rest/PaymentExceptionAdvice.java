package com.biletflow.biletflow.paymentsmonetization.rest;

import com.biletflow.biletflow.common.application.AuthorizationException;
import com.biletflow.biletflow.common.application.EntityNotFoundException;
import com.biletflow.biletflow.common.rest.ApiProblems;
import com.biletflow.biletflow.common.security.UnauthenticatedActorException;
import com.biletflow.biletflow.paymentsmonetization.application.PaymentProviderUnavailableException;
import com.biletflow.biletflow.paymentsmonetization.domain.exceptions.PaymentsDomainException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@RestControllerAdvice(basePackages = { "com.biletflow.biletflow.paymentsmonetization.rest", "com.biletflow.biletflow.ordercheckout.rest" })
public class PaymentExceptionAdvice {

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(EntityNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Resource not found", exception.getMessage());
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
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", exception.getMessage());
    }

    @ExceptionHandler({ IllegalStateException.class, PaymentsDomainException.class })
    public ResponseEntity<ProblemDetail> handleConflict(RuntimeException exception) {
        return problem(HttpStatus.CONFLICT, "Operation rejected", exception.getMessage());
    }

    @ExceptionHandler(PaymentProviderUnavailableException.class)
    public ResponseEntity<ProblemDetail> handleUnavailable(PaymentProviderUnavailableException exception) {
        return ApiProblems.response(
            ApiProblems.body(
                HttpStatus.SERVICE_UNAVAILABLE,
                "payment-provider-unavailable",
                "Payment provider unavailable",
                exception.getMessage()
            )
        );
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String title, String detail) {
        return ApiProblems.response(ApiProblems.body(status, null, title, detail));
    }
}

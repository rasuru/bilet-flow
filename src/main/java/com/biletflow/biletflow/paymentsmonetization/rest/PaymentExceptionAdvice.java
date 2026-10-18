package com.biletflow.biletflow.paymentsmonetization.rest;

import com.biletflow.biletflow.common.application.EntityNotFoundException;
import com.biletflow.biletflow.paymentsmonetization.infrastructure.halyk.HalykGateway;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@Order(-10)
@RestControllerAdvice(basePackages = { "com.biletflow.biletflow.paymentsmonetization.rest", "com.biletflow.biletflow.ordercheckout.rest" })
public class PaymentExceptionAdvice {

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail badRequest(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail conflict(IllegalStateException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ProblemDetail missing(EntityNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(HalykGateway.GatewayUnavailable.class)
    public ProblemDetail unavailable(HalykGateway.GatewayUnavailable e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ProblemDetail denied(org.springframework.security.access.AccessDeniedException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.getMessage());
    }
}

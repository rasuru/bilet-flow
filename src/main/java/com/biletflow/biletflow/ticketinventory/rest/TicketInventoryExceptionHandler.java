package com.biletflow.biletflow.ticketinventory.rest;

import com.biletflow.biletflow.common.application.AuthorizationException;
import com.biletflow.biletflow.common.application.EntityNotFoundException;
import com.biletflow.biletflow.common.rest.ApiProblems;
import com.biletflow.biletflow.common.security.UnauthenticatedActorException;
import com.biletflow.biletflow.ticketinventory.application.delivery.TicketDeliveryCooldownException;
import com.biletflow.biletflow.ticketinventory.application.ticket.TicketNotFoundException;
import com.biletflow.biletflow.ticketinventory.domain.ticket.CheckInStateException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "com.biletflow.biletflow.ticketinventory.rest")
public class TicketInventoryExceptionHandler {

    @ExceptionHandler(TicketNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleTicketNotFound(TicketNotFoundException exception) {
        return ApiProblems.response(ApiProblems.body(HttpStatus.NOT_FOUND, "ticket-not-found", "Ticket not found", exception.getMessage()));
    }

    @ExceptionHandler(CheckInStateException.class)
    public ResponseEntity<ProblemDetail> handleCheckInState(CheckInStateException exception) {
        ProblemDetail problem = ApiProblems.body(
            HttpStatus.CONFLICT,
            "ticket-check-in-state-conflict",
            "Ticket state rejects check-in operation",
            exception.getMessage()
        );
        problem.setProperty("ticketStatus", exception.status().name());
        return ApiProblems.response(problem);
    }

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

    @ExceptionHandler({ IllegalStateException.class, TicketDeliveryCooldownException.class })
    public ResponseEntity<ProblemDetail> handleConflict(RuntimeException exception) {
        return problem(HttpStatus.CONFLICT, "Operation rejected", exception.getMessage());
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String title, String detail) {
        return ApiProblems.response(ApiProblems.body(status, null, title, detail));
    }
}

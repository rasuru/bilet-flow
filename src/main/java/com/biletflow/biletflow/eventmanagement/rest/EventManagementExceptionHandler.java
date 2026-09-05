package com.biletflow.biletflow.eventmanagement.rest;

import com.biletflow.biletflow.common.application.AuthorizationException;
import com.biletflow.biletflow.common.security.UnauthenticatedActorException;
import com.biletflow.biletflow.eventmanagement.domain.exceptions.InvalidEventStateException;
import com.biletflow.biletflow.eventmanagement.domain.exceptions.SocialEventNotFoundException;
import com.biletflow.biletflow.eventmanagement.domain.exceptions.VenueLayoutNotFoundException;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "com.biletflow.biletflow.eventmanagement.rest")
public class EventManagementExceptionHandler {

    @ExceptionHandler({ SocialEventNotFoundException.class, VenueLayoutNotFoundException.class })
    public ResponseEntity<ProblemDetail> handleNotFound(RuntimeException exception) {
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

    @ExceptionHandler({
        IllegalArgumentException.class,
        ConstraintViolationException.class,
        MethodArgumentNotValidException.class,
        HttpMessageNotReadableException.class,
    })
    public ResponseEntity<ProblemDetail> handleBadRequest(Exception exception) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", messageFor(exception));
    }

    @ExceptionHandler({ InvalidEventStateException.class, IllegalStateException.class })
    public ResponseEntity<ProblemDetail> handleConflict(RuntimeException exception) {
        return problem(HttpStatus.CONFLICT, "Operation rejected", exception.getMessage());
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail == null ? status.getReasonPhrase() : detail);
        problem.setTitle(title);
        problem.setType(URI.create("about:blank"));
        return ResponseEntity.status(status).body(problem);
    }

    private String messageFor(Exception exception) {
        if (exception instanceof MethodArgumentNotValidException validationException) {
            return validationException
                .getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("Request validation failed");
        }

        if (exception instanceof HttpMessageNotReadableException) {
            return "Malformed or unreadable request body";
        }

        return exception.getMessage();
    }
}

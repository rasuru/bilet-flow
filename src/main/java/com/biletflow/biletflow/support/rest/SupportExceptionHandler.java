package com.biletflow.biletflow.support.rest;

import com.biletflow.biletflow.common.application.AuthorizationException;
import com.biletflow.biletflow.common.rest.ApiProblems;
import com.biletflow.biletflow.common.security.UnauthenticatedActorException;
import com.biletflow.biletflow.eventmanagement.domain.exceptions.SocialEventNotFoundException;
import com.biletflow.biletflow.support.domain.exceptions.InvalidSupportCaseStateException;
import com.biletflow.biletflow.support.domain.exceptions.SupportCaseNotFoundException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = { SupportAdministrationResource.class, SupportIntakeResource.class })
public class SupportExceptionHandler {

    @ExceptionHandler({
        AuthorizationException.class, UnauthenticatedActorException.class, SupportCaseNotFoundException.class,
        SocialEventNotFoundException.class, InvalidSupportCaseStateException.class, IllegalArgumentException.class,
    })
    public ResponseEntity<ProblemDetail> supportError(RuntimeException exception) {
        var status = switch (exception) {
            case AuthorizationException ignored -> HttpStatus.FORBIDDEN;
            case UnauthenticatedActorException ignored -> HttpStatus.UNAUTHORIZED;
            case SupportCaseNotFoundException ignored -> HttpStatus.NOT_FOUND;
            case SocialEventNotFoundException ignored -> HttpStatus.NOT_FOUND;
            case InvalidSupportCaseStateException ignored -> HttpStatus.CONFLICT;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ApiProblems.response(ApiProblems.body(status, null, status.getReasonPhrase(), exception.getMessage()));
    }
}

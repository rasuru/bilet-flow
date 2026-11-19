package com.biletflow.biletflow.iam.rest.errors;

import static org.springframework.core.annotation.AnnotatedElementUtils.findMergedAnnotation;

import com.biletflow.biletflow.common.rest.ApiProblems;
import com.biletflow.biletflow.iam.application.UsernameAlreadyUsedException;
import com.biletflow.biletflow.iam.security.UserNotActivatedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import tech.jhipster.web.util.HeaderUtil;

/** Translates shared MVC and IAM failures into RFC 9457 Problem Details. */
@ControllerAdvice
public class ExceptionTranslator extends ResponseEntityExceptionHandler {

    private static final String FIELD_ERRORS_KEY = "fieldErrors";
    private static final String MESSAGE_KEY = "message";
    private static final String PATH_KEY = "path";

    private static final Logger LOG = LoggerFactory.getLogger(ExceptionTranslator.class);

    @Value("${jhipster.clientApp.name:biletFlow}")
    private String applicationName;

    @ExceptionHandler
    public ResponseEntity<Object> handleAnyException(Exception ex, NativeWebRequest request) {
        ProblemDetail problem = problemFor(ex, toStatus(ex));
        return handleExceptionInternal(ex, problem, buildHeaders(ex), HttpStatusCode.valueOf(problem.getStatus()), request);
    }

    @Nullable
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
        Exception ex,
        @Nullable Object body,
        HttpHeaders headers,
        HttpStatusCode statusCode,
        WebRequest request
    ) {
        if (body != null && !(body instanceof ProblemDetail)) {
            return super.handleExceptionInternal(ex, body, headers, statusCode, request);
        }

        ProblemDetail problem = body instanceof ProblemDetail supplied ? supplied : problemFor(ex, statusCode);
        problem.setStatus(statusCode.value());
        if (statusCode.is5xxServerError()) {
            LOG.error("Unexpected REST error", ex);
            problem.setDetail("An unexpected server error occurred.");
        } else if (ex instanceof HttpMessageNotReadableException) {
            problem.setDetail("Malformed or unreadable request body");
        }

        if (problem.getType() == null || ApiProblems.GENERIC_TYPE.equals(problem.getType())) {
            problem.setType(getMappedType(ex));
        }
        if (problem.getProperties() == null || !problem.getProperties().containsKey(MESSAGE_KEY)) {
            String key = getMappedMessageKey(ex);
            problem.setProperty(MESSAGE_KEY, key == null ? "error.http." + statusCode.value() : key);
        }
        if (request instanceof NativeWebRequest nativeRequest) {
            HttpServletRequest servletRequest = nativeRequest.getNativeRequest(HttpServletRequest.class);
            if (servletRequest != null) problem.setProperty(PATH_KEY, URI.create(servletRequest.getRequestURI()));
        }
        if (isRequestValidation(ex)) {
            problem.setProperty(FIELD_ERRORS_KEY, getFieldErrors(ex));
        }

        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.putAll(headers);
        responseHeaders.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        return super.handleExceptionInternal(ex, problem, responseHeaders, statusCode, request);
    }

    private ProblemDetail problemFor(Exception exception, HttpStatusCode status) {
        if (exception instanceof UsernameAlreadyUsedException) return new LoginAlreadyUsedException().getBody();
        if (exception instanceof com.biletflow.biletflow.iam.application.EmailAlreadyUsedException) {
            return new EmailAlreadyUsedException().getBody();
        }
        if (exception instanceof com.biletflow.biletflow.iam.application.InvalidPasswordException) {
            return new InvalidPasswordException().getBody();
        }
        if (exception instanceof ErrorResponse error) return error.getBody();

        ProblemDetail problem = ProblemDetail.forStatus(status);
        var responseStatus = resolveResponseStatus(exception);
        if (responseStatus != null && !responseStatus.reason().isBlank()) {
            problem.setTitle(responseStatus.reason());
        }
        if (!status.is5xxServerError()) {
            problem.setDetail(exception.getMessage());
        }
        return problem;
    }

    private List<FieldErrorVM> getFieldErrors(Throwable exception) {
        if (exception instanceof MethodArgumentNotValidException invalidBody) {
            return invalidBody.getBindingResult().getFieldErrors().stream().map(this::toFieldError).toList();
        }
        if (exception instanceof ConstraintViolationException invalidConstraint) {
            return invalidConstraint
                .getConstraintViolations()
                .stream()
                .map(violation -> {
                    String path = violation.getPropertyPath().toString();
                    int separator = path.indexOf('.');
                    return new FieldErrorVM(
                        violation.getRootBeanClass().getSimpleName(),
                        separator < 0 ? path : path.substring(separator + 1),
                        violation.getMessage(),
                        violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName()
                    );
                })
                .toList();
        }
        var validation = (HandlerMethodValidationException) exception;
        List<FieldErrorVM> errors = new ArrayList<>();
        for (var result : validation.getParameterValidationResults()) {
            if (result instanceof ParameterErrors bean) {
                bean.getFieldErrors().stream().map(this::toFieldError).forEach(errors::add);
                continue;
            }
            var parameter = result.getMethodParameter();
            String field = parameter.getParameterName();
            if (field == null) continue;
            for (var error : result.getResolvableErrors()) {
                String[] codes = error.getCodes();
                String code = codes == null || codes.length == 0 ? null : codes[codes.length - 1];
                errors.add(new FieldErrorVM(parameter.getContainingClass().getSimpleName(), field, error.getDefaultMessage(), code));
            }
        }
        return errors;
    }

    private FieldErrorVM toFieldError(FieldError error) {
        return new FieldErrorVM(
            error.getObjectName().replaceFirst("DTO$", ""),
            error.getField(),
            StringUtils.defaultIfBlank(error.getDefaultMessage(), error.getCode()),
            error.getCode()
        );
    }

    private boolean isRequestValidation(Throwable error) {
        return (
            error instanceof MethodArgumentNotValidException ||
            error instanceof ConstraintViolationException ||
            (error instanceof HandlerMethodValidationException validation && !validation.isForReturnValue())
        );
    }

    private HttpStatusCode toStatus(Throwable exception) {
        if (exception instanceof ErrorResponse error) return error.getStatusCode();

        return Optional.ofNullable(getMappedStatus(exception)).orElseGet(() ->
            Optional.ofNullable(resolveResponseStatus(exception)).map(ResponseStatus::value).orElse(HttpStatus.INTERNAL_SERVER_ERROR)
        );
    }

    private ResponseStatus resolveResponseStatus(final Throwable type) {
        final ResponseStatus candidate = findMergedAnnotation(type.getClass(), ResponseStatus.class);
        return candidate == null && type.getCause() != null ? resolveResponseStatus(type.getCause()) : candidate;
    }

    private URI getMappedType(Throwable err) {
        if (isRequestValidation(err)) return ErrorConstants.CONSTRAINT_VIOLATION_TYPE;
        if (err instanceof BadCredentialsException) return ApiProblems.type("invalid-credentials");
        if (err instanceof ConcurrencyFailureException) return ApiProblems.type("concurrent-update");
        if (err instanceof HttpMessageNotReadableException) return ApiProblems.type("malformed-request");
        return ApiProblems.GENERIC_TYPE;
    }

    private String getMappedMessageKey(Throwable err) {
        if (isRequestValidation(err)) {
            return ErrorConstants.ERR_VALIDATION;
        } else if (err instanceof ConcurrencyFailureException || err.getCause() instanceof ConcurrencyFailureException) {
            return ErrorConstants.ERR_CONCURRENCY_FAILURE;
        }
        return null;
    }

    private HttpStatus getMappedStatus(Throwable err) {
        // Where we disagree with Spring defaults
        if (err instanceof AccessDeniedException) return HttpStatus.FORBIDDEN;
        if (err instanceof ConcurrencyFailureException) return HttpStatus.CONFLICT;
        if (err instanceof BadCredentialsException) return HttpStatus.UNAUTHORIZED;
        if (err instanceof ConstraintViolationException) return HttpStatus.BAD_REQUEST;
        return null;
    }

    private HttpHeaders buildHeaders(Throwable err) {
        return err instanceof BadRequestAlertException badRequestAlertException
            ? HeaderUtil.createFailureAlert(
                  applicationName,
                  true,
                  badRequestAlertException.getEntityName(),
                  badRequestAlertException.getErrorKey(),
                  badRequestAlertException.getMessage()
              )
            : HttpHeaders.EMPTY;
    }

    @ExceptionHandler(UserNotActivatedException.class)
    public ResponseEntity<ProblemDetail> handleUserNotActivatedException(UserNotActivatedException ex) {
        ProblemDetail problemDetail = ApiProblems.body(
            HttpStatus.UNAUTHORIZED,
            "account-not-activated",
            "User Not Activated",
            ex.getMessage()
        );
        problemDetail.setProperty("errorKey", "usernotactivated");

        return ApiProblems.response(problemDetail);
    }
}

package com.biletflow.biletflow.common.rest;

import java.net.URI;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponseException;

public final class ApiProblems {

    public static final URI GENERIC_TYPE = URI.create("about:blank");

    private static final String CATALOG = "https://github.com/rasuru/bilet-flow/blob/main/docs/api-errors.md#";

    private ApiProblems() {}

    public static URI type(String name) {
        return URI.create(CATALOG + name);
    }

    public static ProblemDetail body(HttpStatus status, @Nullable String type, String title, @Nullable String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail == null ? title : detail);
        problem.setTitle(title);
        problem.setType(type == null ? GENERIC_TYPE : type(type));
        return problem;
    }

    public static ResponseEntity<ProblemDetail> response(ProblemDetail problem) {
        return ResponseEntity.status(problem.getStatus()).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem);
    }

    public static ErrorResponseException exception(HttpStatus status, @Nullable String type, String title, @Nullable String detail) {
        return new ErrorResponseException(status, body(status, type, title, detail), null);
    }
}

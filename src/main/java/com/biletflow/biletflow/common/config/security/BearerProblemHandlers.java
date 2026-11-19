package com.biletflow.biletflow.common.config.security;

import com.biletflow.biletflow.common.rest.ApiProblems;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import tools.jackson.databind.ObjectMapper;

final class BearerProblemHandlers {

    private final BearerTokenAuthenticationEntryPoint entryPoint = new BearerTokenAuthenticationEntryPoint();
    private final BearerTokenAccessDeniedHandler deniedHandler = new BearerTokenAccessDeniedHandler();
    private final ObjectMapper mapper;

    BearerProblemHandlers(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    void unauthenticated(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException {
        entryPoint.commence(request, response, exception);
        writeProblem(request, response);
    }

    void forbidden(HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception) throws IOException {
        deniedHandler.handle(request, response, exception);
        writeProblem(request, response);
    }

    private void writeProblem(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (response.isCommitted() || !request.getRequestURI().startsWith(request.getContextPath() + "/api/")) {
            return;
        }
        HttpStatus status = HttpStatus.valueOf(response.getStatus());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(), ApiProblems.body(status, null, status.getReasonPhrase(), null));
    }
}

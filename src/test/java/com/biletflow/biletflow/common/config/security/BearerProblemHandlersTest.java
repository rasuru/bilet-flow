package com.biletflow.biletflow.common.config.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.resource.BearerTokenError;
import tools.jackson.databind.json.JsonMapper;

class BearerProblemHandlersTest {

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final BearerProblemHandlers handlers = new BearerProblemHandlers(mapper);

    @Test
    void authenticationFailureRetainsBearerChallengeAndStatus() throws Exception {
        assertProblem(new BadCredentialsException("Missing token"), 401, "Bearer");
    }

    @Test
    void malformedBearerRequestKeepsTheStatusSelectedBySpringSecurity() throws Exception {
        var error = new BearerTokenError("invalid_request", HttpStatus.BAD_REQUEST, "Malformed bearer token", null);
        assertProblem(new OAuth2AuthenticationException(error), 400, "Bearer error=\"invalid_request\"");
    }

    private void assertProblem(AuthenticationException exception, int status, String challengePrefix) throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/account");
        var response = new MockHttpServletResponse();

        handlers.unauthenticated(request, response, exception);

        assertThat(response.getStatus()).isEqualTo(status);
        assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).startsWith(challengePrefix);
        assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        Map<?, ?> body = mapper.readValue(response.getContentAsByteArray(), Map.class);
        assertThat(body.get("status")).isEqualTo(status);
        assertThat(body.get("type")).isEqualTo("about:blank");
    }
}

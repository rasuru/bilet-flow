package com.biletflow.biletflow.iam.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AuthenticateRequest(
    @NotBlank @Size(max = 254) String username,

    @NotBlank @Size(min = 4, max = 100) String password,

    boolean rememberMe
) {}

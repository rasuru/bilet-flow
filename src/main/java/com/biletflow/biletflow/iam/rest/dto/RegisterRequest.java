package com.biletflow.biletflow.iam.rest.dto;

import com.biletflow.biletflow.common.config.Constants;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank @Pattern(regexp = Constants.LOGIN_REGEX) @Size(min = 1, max = 50) String login,

    @Size(max = 50) String firstName,

    @Size(max = 50) String lastName,

    @NotBlank @Email @Size(min = 5, max = 254) String email,

    @NotBlank @Size(min = 4, max = 100) String password,

    @Size(min = 2, max = 10) String langKey
) {}

package com.biletflow.biletflow.iam.rest.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetInitRequest(@NotBlank @Email @Size(min = 5, max = 254) String email) {}

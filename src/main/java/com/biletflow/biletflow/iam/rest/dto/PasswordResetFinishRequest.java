package com.biletflow.biletflow.iam.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetFinishRequest(@NotBlank String key, @NotBlank @Size(min = 4, max = 100) String newPassword) {}

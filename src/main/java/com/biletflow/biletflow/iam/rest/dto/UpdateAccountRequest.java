package com.biletflow.biletflow.iam.rest.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record UpdateAccountRequest(
    @Size(max = 50) String firstName,

    @Size(max = 50) String lastName,

    @Email @Size(min = 5, max = 254) String email,

    @Size(min = 2, max = 10) String langKey,

    @Size(max = 256) String imageUrl
) {}

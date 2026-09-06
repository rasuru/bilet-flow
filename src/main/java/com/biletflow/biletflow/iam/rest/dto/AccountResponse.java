package com.biletflow.biletflow.iam.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Set;

public record AccountResponse(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id,

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String login,

    String firstName,

    String lastName,

    String email,

    String imageUrl,

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean activated,

    String langKey,

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Set<String> authorities
) {}

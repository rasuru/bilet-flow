package com.biletflow.biletflow.iam.rest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

public record AuthenticateResponse(@JsonProperty("id_token") @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String idToken) {}

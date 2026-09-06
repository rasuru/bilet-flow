package com.biletflow.biletflow.iam.rest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AuthenticateResponse(@JsonProperty("id_token") String idToken) {}

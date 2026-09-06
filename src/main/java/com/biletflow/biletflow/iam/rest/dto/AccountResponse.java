package com.biletflow.biletflow.iam.rest.dto;

import java.util.Set;

public record AccountResponse(
    Long id,
    String login,
    String firstName,
    String lastName,
    String email,
    String imageUrl,
    boolean activated,
    String langKey,
    Set<String> authorities
) {}

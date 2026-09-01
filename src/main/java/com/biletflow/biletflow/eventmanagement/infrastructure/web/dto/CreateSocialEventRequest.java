package com.biletflow.biletflow.eventmanagement.infrastructure.web.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record CreateSocialEventRequest(
    @NotBlank String title,
    String description,
    @NotBlank String category,
    String imageUrl,
    @NotBlank String visibility,
    @NotNull @Future Instant startAt,
    @NotNull @Future Instant endAt,
    @NotBlank String regWindowType,
    Instant regWindowStart,
    Instant regWindowEnd
) {}

package com.biletflow.biletflow.eventmanagement.infrastructure.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;

public record CreateTicketTypeRequest(
    @NotBlank String name,
    String description,
    @NotBlank String pricingType,
    BigDecimal priceAmount,
    String priceCurrency,
    @Min(1) int totalQuantity,
    @NotNull Instant salesStart,
    @NotNull Instant salesEnd,
    @Min(1) int maxPerOrder
) {}

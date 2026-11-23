package com.biletflow.biletflow.promotions.application.redemption.command;

import com.biletflow.biletflow.common.domain.Money;
import java.util.UUID;

public record PromotionLine(UUID ticketTypeId, Money unitPrice, int quantity) {}

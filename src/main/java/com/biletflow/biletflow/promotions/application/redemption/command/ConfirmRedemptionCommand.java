package com.biletflow.biletflow.promotions.application.redemption.command;

import java.util.UUID;

public record ConfirmRedemptionCommand(UUID redemptionId, UUID orderId) {}

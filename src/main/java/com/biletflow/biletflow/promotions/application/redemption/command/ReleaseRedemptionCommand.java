package com.biletflow.biletflow.promotions.application.redemption.command;

import java.util.UUID;

public record ReleaseRedemptionCommand(UUID redemptionId, UUID checkoutId) {}

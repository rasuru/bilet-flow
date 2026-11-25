package com.biletflow.biletflow.eventmanagement.application.socialevent.command;

import com.biletflow.biletflow.eventmanagement.domain.SocialEventId;
import java.util.Objects;

public record DefineRefundPolicyCommand(SocialEventId eventId, boolean refundsAllowed, int cutoffHoursBeforeStart, String terms) {
    public DefineRefundPolicyCommand {
        Objects.requireNonNull(eventId, "SocialEventId cannot be null");
    }
}

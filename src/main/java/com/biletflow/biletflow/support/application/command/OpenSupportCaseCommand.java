package com.biletflow.biletflow.support.application.command;

import com.biletflow.biletflow.support.domain.supportcase.IssueCategory;
import com.biletflow.biletflow.support.domain.supportcase.SupportCaseType;
import java.util.UUID;

public record OpenSupportCaseCommand(
    SupportCaseType type,
    String subject,
    IssueCategory category,
    String initialMessage,
    UUID eventId,
    UUID orderId,
    UUID ticketId
) {}

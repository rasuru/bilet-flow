package com.biletflow.biletflow.support.application.command;

import java.util.UUID;

public record AssignSupportCaseCommand(UUID caseId, Long supportUserId) {}

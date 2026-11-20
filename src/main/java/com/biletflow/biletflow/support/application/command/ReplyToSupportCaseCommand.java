package com.biletflow.biletflow.support.application.command;

import java.util.UUID;

public record ReplyToSupportCaseCommand(UUID caseId, String content) {}

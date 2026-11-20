package com.biletflow.biletflow.support.application.command;

import com.biletflow.biletflow.support.domain.supportcase.CaseStatus;
import java.util.UUID;

public record ChangeSupportCaseStatusCommand(UUID caseId, CaseStatus status) {}

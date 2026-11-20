package com.biletflow.biletflow.support.ohs;

import com.biletflow.biletflow.support.application.command.OpenSupportCaseCommand;
import com.biletflow.biletflow.support.application.command.ReplyToSupportCaseCommand;
import com.biletflow.biletflow.support.ohs.dto.SupportCaseSummary;
import java.util.List;
import java.util.UUID;

/** Published in-process contract for other bounded contexts. Never expose JPA/domain objects. */
public interface SupportOhs {
    SupportCaseSummary getCase(UUID caseId);
    List<SupportCaseSummary> myCases();
    SupportCaseSummary openCase(OpenSupportCaseCommand command);
    SupportCaseSummary addMessage(UUID caseId, String content);
}

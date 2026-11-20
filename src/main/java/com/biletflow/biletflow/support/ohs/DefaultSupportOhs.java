package com.biletflow.biletflow.support.ohs;

import com.biletflow.biletflow.support.application.SupportCaseApplicationService;
import com.biletflow.biletflow.support.application.command.OpenSupportCaseCommand;
import com.biletflow.biletflow.support.application.command.ReplyToSupportCaseCommand;
import com.biletflow.biletflow.support.ohs.dto.SupportCaseSummary;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** In-process OHS adapter. The caller's identity must be established by the shared security context. */
@Component
public class DefaultSupportOhs implements SupportOhs {
    private final SupportCaseApplicationService cases;

    public DefaultSupportOhs(SupportCaseApplicationService cases) { this.cases = cases; }

    @Override public SupportCaseSummary getCase(UUID caseId) {
        return SupportCaseSummary.from(cases.get(caseId));
    }

    @Override public List<SupportCaseSummary> myCases() {
        return cases.list().stream().map(SupportCaseSummary::from).toList();
    }

    @Override public SupportCaseSummary openCase(OpenSupportCaseCommand command) {
        return SupportCaseSummary.from(cases.open(command));
    }

    @Override public SupportCaseSummary addMessage(UUID caseId, String content) {
        return SupportCaseSummary.from(cases.reply(new ReplyToSupportCaseCommand(caseId, content)));
    }
}

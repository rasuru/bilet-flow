package com.biletflow.biletflow.support.domain.exceptions;

public class SupportCaseNotFoundException extends SupportCaseDomainException {

    public SupportCaseNotFoundException(String caseId) {
        super("Support case not found: " + caseId);
    }
}

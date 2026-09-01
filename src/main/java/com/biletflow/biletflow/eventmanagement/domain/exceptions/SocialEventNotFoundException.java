package com.biletflow.biletflow.eventmanagement.domain.exceptions;

public class SocialEventNotFoundException extends SocialEventDomainException {

    public SocialEventNotFoundException(String identifier) {
        super("SocialEvent not found with identifier: " + identifier);
    }
}

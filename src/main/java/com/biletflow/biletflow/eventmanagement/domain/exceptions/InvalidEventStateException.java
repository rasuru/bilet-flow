package com.biletflow.biletflow.eventmanagement.domain.exceptions;

public class InvalidEventStateException extends SocialEventDomainException {

    public InvalidEventStateException(String message) {
        super(message);
    }
}

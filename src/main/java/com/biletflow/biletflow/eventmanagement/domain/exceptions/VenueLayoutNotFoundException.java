package com.biletflow.biletflow.eventmanagement.domain.exceptions;

public class VenueLayoutNotFoundException extends SocialEventDomainException {

    public VenueLayoutNotFoundException(String identifier) {
        super("VenueLayout not found with identifier: " + identifier);
    }
}

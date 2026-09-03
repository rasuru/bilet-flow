package com.biletflow.biletflow.ticketinventory.application.common;

public class EntityNotFoundException extends RuntimeException {

    public EntityNotFoundException(String message) {
        super(message);
    }
}

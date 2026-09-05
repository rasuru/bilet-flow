package com.biletflow.biletflow.common.security;

public class UnauthenticatedActorException extends RuntimeException {

    public UnauthenticatedActorException(String message) {
        super(message);
    }
}

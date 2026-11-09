package com.linkscale.backend.exception;

public class UnauthenticatedCreationDisabledException extends RuntimeException {
    public UnauthenticatedCreationDisabledException(String message) {
        super(message);
    }
}


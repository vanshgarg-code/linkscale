package com.linkscale.backend.exception;

import org.springframework.http.HttpStatus;

public class RateLimitException extends RuntimeException {

    private final HttpStatus status;
    private final long retryAfterSeconds;

    public RateLimitException(HttpStatus status, long retryAfterSeconds, String message) {
        super(message);
        this.status = status;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public HttpStatus status() {
        return status;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}

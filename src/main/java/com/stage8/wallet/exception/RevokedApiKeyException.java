package com.stage8.wallet.exception;

public class RevokedApiKeyException extends RuntimeException {
    public RevokedApiKeyException() {
        super("API key has been revoked");
    }

    public RevokedApiKeyException(String message) {
        super(message);
    }
}

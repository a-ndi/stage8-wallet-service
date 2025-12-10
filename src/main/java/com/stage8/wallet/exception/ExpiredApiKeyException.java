package com.stage8.wallet.exception;

public class ExpiredApiKeyException extends RuntimeException {
    public ExpiredApiKeyException() {
        super("API key has expired");
    }

    public ExpiredApiKeyException(String message) {
        super(message);
    }
}

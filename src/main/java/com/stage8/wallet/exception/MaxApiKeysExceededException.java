package com.stage8.wallet.exception;

public class MaxApiKeysExceededException extends RuntimeException {
    public MaxApiKeysExceededException() {
        super("Maximum number of active API keys (5) exceeded");
    }

    public MaxApiKeysExceededException(String message) {
        super(message);
    }
}

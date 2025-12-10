package com.stage8.wallet.exception;

public class InvalidExpiryException extends RuntimeException {
    public InvalidExpiryException() {
        super("Invalid expiry format. Must be 1H, 1D, 1M, or 1Y");
    }

    public InvalidExpiryException(String message) {
        super(message);
    }
}

package com.stage8.wallet.security;

/**
 * Exception for API key authentication failures
 * Used to provide clear error messages for API key validation issues
 */
public class ApiKeyAuthenticationException extends RuntimeException {
    private final ApiKeyErrorType errorType;

    public ApiKeyAuthenticationException(ApiKeyErrorType errorType, String message) {
        super(message);
        this.errorType = errorType;
    }

    public ApiKeyErrorType getErrorType() {
        return errorType;
    }

    public enum ApiKeyErrorType {
        INVALID_KEY,
        EXPIRED_KEY,
        REVOKED_KEY,
        MISSING_PERMISSIONS
    }
}


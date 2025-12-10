package com.stage8.wallet.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Custom authentication entry point to provide clear error messages
 * for authentication failures (invalid/expired API keys, invalid JWT, etc.)
 */
@Slf4j
@Component
public class SecurityErrorHandler implements AuthenticationEntryPoint {

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException {
        // Check if API key was provided
        String apiKey = request.getHeader("x-api-key");
        String authHeader = request.getHeader("Authorization");

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        String errorMessage;
        if (apiKey != null && !apiKey.isEmpty()) {
            // API key authentication failed
            errorMessage = "Invalid or expired API key. Please check your API key and ensure it is not revoked or expired.";
            log.warn("API key authentication failed for request: {}", request.getRequestURI());
        } else if (authHeader != null && authHeader.startsWith("Bearer ")) {
            // JWT authentication failed
            errorMessage = "Invalid or expired JWT token. Please provide a valid authentication token.";
            log.warn("JWT authentication failed for request: {}", request.getRequestURI());
        } else {
            // No authentication provided
            errorMessage = "Authentication required. Please provide a valid JWT token (Authorization: Bearer <token>) or API key (x-api-key header).";
            log.warn("No authentication provided for request: {}", request.getRequestURI());
        }

        String jsonResponse = String.format(
                "{\"error\": \"%s\"}",
                errorMessage.replace("\"", "\\\"")
        );

        response.getWriter().write(jsonResponse);
        response.getWriter().flush();
    }
}


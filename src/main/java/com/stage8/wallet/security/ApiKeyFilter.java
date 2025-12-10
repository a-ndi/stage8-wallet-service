package com.stage8.wallet.security;

import com.stage8.wallet.model.entity.ApiKeyEntity;
import com.stage8.wallet.repository.ApiKeyRepository;
import com.stage8.wallet.service.ApiKeyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApiKeyFilter extends OncePerRequestFilter {

    private final ApiKeyRepository apiKeyRepository;
    private final ApiKeyService apiKeyService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        final String apiKey = request.getHeader("x-api-key");

        // If no API key header, continue to next filter (JWT or other auth)
        if (apiKey == null || apiKey.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            // Hash the provided API key using the service method
            String keyHash = apiKeyService.hashApiKey(apiKey);

            // Look up API key in database
            Optional<ApiKeyEntity> apiKeyEntityOpt = apiKeyRepository.findByKeyHash(keyHash);

            if (apiKeyEntityOpt.isEmpty()) {
                // API key not found - continue without authentication
                // Spring Security will reject if authentication is required
                log.debug("API key not found in database");
                filterChain.doFilter(request, response);
                return;
            }

            ApiKeyEntity apiKeyEntity = apiKeyEntityOpt.get();

            // Validate API key (not revoked and not expired)
            String validationError = validateApiKey(apiKeyEntity);
            if (validationError != null) {
                log.warn("API key validation failed: {}", validationError);
                // Continue without authentication - Spring Security will handle rejection
                filterChain.doFilter(request, response);
                return;
            }

            // Set authentication in security context
            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                String userId = apiKeyEntity.getOwner() != null 
                        ? apiKeyEntity.getOwner().getId().toString() 
                        : "api-key-user";

                // Convert permissions to authorities
                Collection<GrantedAuthority> authorities = apiKeyEntity.getPermissions() != null
                        ? apiKeyEntity.getPermissions().stream()
                                .map(permission -> new SimpleGrantedAuthority("ROLE_" + permission.name()))
                                .collect(Collectors.toList())
                        : null;

                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        userId,
                        apiKeyEntity, // Store API key entity for permission checking
                        authorities
                );
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
                log.debug("API key authenticated successfully for user: {}", userId);
            }

        } catch (Exception e) {
            log.error("Error processing API key authentication", e);
            // Continue without authentication - Spring Security will handle rejection
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Validates if an API key is valid (not revoked and not expired)
     * @return Error message if invalid, null if valid
     */
    private String validateApiKey(ApiKeyEntity apiKeyEntity) {
        // Check if revoked
        if (apiKeyEntity.isRevoked()) {
            return "API key has been revoked";
        }

        // Check if expired
        if (apiKeyEntity.getExpiresAt() != null && apiKeyEntity.getExpiresAt().isBefore(Instant.now())) {
            return "API key has expired";
        }

        return null; // Valid
    }

}


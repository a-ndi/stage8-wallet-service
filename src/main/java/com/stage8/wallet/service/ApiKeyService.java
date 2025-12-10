package com.stage8.wallet.service;

import com.stage8.wallet.model.entity.ApiKeyEntity;
import com.stage8.wallet.model.entity.UserEntity;
import com.stage8.wallet.model.enums.Permission;
import com.stage8.wallet.repository.ApiKeyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ApiKeyService {

    private static final int MAX_ACTIVE_KEYS = 5;
    private final ApiKeyRepository apiKeyRepository;

    /**
     * Creates a new API key for a user
     * Validates expiry, permissions, and enforces max 5 active keys
     */
    @Transactional
    public CreateApiKeyResult createApiKey(UserEntity user, String name, String expiry, Set<Permission> permissions) {
        // Validate name
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Name is required");
        }

        // Validate and parse expiry
        Instant expiresAt = parseExpiry(expiry);
        if (expiresAt.isBefore(Instant.now())) {
            throw new IllegalArgumentException("Expiry date must be in the future");
        }

        // Validate permissions
        if (permissions == null || permissions.isEmpty()) {
            throw new IllegalArgumentException("At least one permission is required");
        }

        // Check for max 5 active keys
        List<ApiKeyEntity> activeKeys = apiKeyRepository.findByOwnerIdAndRevokedFalse(user.getId());
        if (activeKeys.size() >= MAX_ACTIVE_KEYS) {
            throw new IllegalStateException("Maximum of " + MAX_ACTIVE_KEYS + " active API keys allowed per user");
        }

        // Generate API key
        String plainApiKey = generateApiKey();
        String keyHash = hashApiKey(plainApiKey);

        // Create and save API key entity
        ApiKeyEntity apiKeyEntity = ApiKeyEntity.builder()
                .keyHash(keyHash)
                .name(name.trim())
                .owner(user)
                .expiresAt(expiresAt)
                .permissions(permissions)
                .revoked(false)
                .build();

        apiKeyEntity = apiKeyRepository.save(apiKeyEntity);

        return new CreateApiKeyResult(plainApiKey, apiKeyEntity);
    }

    /**
     * Parses expiry string (1H, 1D, 1M, 1Y) and converts to Instant
     */
    private Instant parseExpiry(String expiry) {
        if (expiry == null || expiry.trim().isEmpty()) {
            throw new IllegalArgumentException("Expiry is required");
        }

        expiry = expiry.trim().toUpperCase();
        Instant now = Instant.now();

        return switch (expiry) {
            case "1H" -> now.plus(1, ChronoUnit.HOURS);
            case "1D" -> now.plus(1, ChronoUnit.DAYS);
            case "1M" -> now.plus(1, ChronoUnit.MONTHS);
            case "1Y" -> now.plus(1, ChronoUnit.YEARS);
            default -> throw new IllegalArgumentException("Expiry must be one of: 1H, 1D, 1M, 1Y");
        };
    }

    /**
     * Generates a secure random API key
     * Format: sk_live_<64 random characters>
     */
    private String generateApiKey() {
        SecureRandom random = new SecureRandom();
        byte[] randomBytes = new byte[48]; // 48 bytes = 64 base64 characters
        random.nextBytes(randomBytes);
        
        String randomPart = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        return "sk_live_" + randomPart;
    }

    /**
     * Hashes an API key using SHA-256
     * This matches the hashing method used in ApiKeyFilter
     */
    public String hashApiKey(String apiKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(apiKey.getBytes(StandardCharsets.UTF_8));
            
            // Convert bytes to hexadecimal string
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Rolls over an expired API key by creating a replacement with duplicated permissions
     * Checks if the key is expired, duplicates permissions, and generates a replacement
     */
    @Transactional
    public CreateApiKeyResult rolloverApiKey(String oldApiKey, String expiry) {
        // Hash the old API key
        String oldKeyHash = hashApiKey(oldApiKey);
        
        // Find the old API key
        ApiKeyEntity oldApiKeyEntity = apiKeyRepository.findByKeyHash(oldKeyHash)
                .orElseThrow(() -> new IllegalArgumentException("API key not found"));

        // Check if the key is expired
        if (oldApiKeyEntity.getExpiresAt() == null || 
            !oldApiKeyEntity.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalStateException("API key is not expired. Only expired keys can be rolled over.");
        }

        // Check if already revoked
        if (oldApiKeyEntity.isRevoked()) {
            throw new IllegalStateException("API key is already revoked");
        }

        // Get user and permissions from old key
        UserEntity user = oldApiKeyEntity.getOwner();
        if (user == null) {
            throw new IllegalStateException("API key has no owner");
        }

        Set<Permission> permissions = oldApiKeyEntity.getPermissions();
        if (permissions == null || permissions.isEmpty()) {
            throw new IllegalStateException("Old API key has no permissions to duplicate");
        }

        // Check for max 5 active keys (excluding the expired one we're replacing)
        List<ApiKeyEntity> activeKeys = apiKeyRepository.findByOwnerIdAndRevokedFalse(user.getId());
        // Remove the expired key from count if it's in the list
        long activeCount = activeKeys.stream()
                .filter(key -> !key.getId().equals(oldApiKeyEntity.getId()))
                .count();
        
        if (activeCount >= MAX_ACTIVE_KEYS) {
            throw new IllegalStateException("Maximum of " + MAX_ACTIVE_KEYS + " active API keys allowed per user");
        }

        // Parse expiry (required for rollover)
        if (expiry == null || expiry.trim().isEmpty()) {
            throw new IllegalArgumentException("Expiry is required for API key rollover");
        }
        Instant expiresAt = parseExpiry(expiry);

        // Generate new API key
        String plainApiKey = generateApiKey();
        String keyHash = hashApiKey(plainApiKey);

        // Create new API key entity with duplicated permissions
        ApiKeyEntity newApiKeyEntity = ApiKeyEntity.builder()
                .keyHash(keyHash)
                .name(oldApiKeyEntity.getName() + " (rolled over)")
                .owner(user)
                .expiresAt(expiresAt)
                .permissions(permissions) // Duplicate permissions
                .revoked(false)
                .build();

        newApiKeyEntity = apiKeyRepository.save(newApiKeyEntity);

        // Revoke the old expired key
        oldApiKeyEntity.setRevoked(true);
        apiKeyRepository.save(oldApiKeyEntity);

        return new CreateApiKeyResult(plainApiKey, newApiKeyEntity);
    }

    /**
     * Result class for API key creation
     */
    public static class CreateApiKeyResult {
        private final String plainApiKey;
        private final ApiKeyEntity apiKeyEntity;

        public CreateApiKeyResult(String plainApiKey, ApiKeyEntity apiKeyEntity) {
            this.plainApiKey = plainApiKey;
            this.apiKeyEntity = apiKeyEntity;
        }

        public String getPlainApiKey() {
            return plainApiKey;
        }

        public ApiKeyEntity getApiKeyEntity() {
            return apiKeyEntity;
        }
    }
}


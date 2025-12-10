package com.stage8.wallet.controller;

import com.stage8.wallet.dto.CreateApiKeyRequest;
import com.stage8.wallet.dto.CreateApiKeyResponse;
import com.stage8.wallet.dto.RolloverApiKeyRequest;
import com.stage8.wallet.model.entity.UserEntity;
import com.stage8.wallet.repository.UserRepository;
import com.stage8.wallet.service.ApiKeyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/keys")
@RequiredArgsConstructor
@Tag(name = "API Key Management", description = "Create and manage API keys for service-to-service authentication")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;
    private final UserRepository userRepository;

    @Operation(
            summary = "Create API Key",
            description = "Creates a new API key for the authenticated user. Maximum 5 active keys per user. " +
                    "Expiry formats: 1H (hour), 1D (day), 1M (month), 1Y (year). " +
                    "API key is only shown once - store it securely.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "API key created successfully",
                    content = @Content(schema = @Schema(implementation = CreateApiKeyResponse.class))
            ),
            @ApiResponse(responseCode = "400", description = "Invalid request (validation error)"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "409", description = "Maximum active keys limit reached (5)")
    })
    @PostMapping("/create")
    public ResponseEntity<?> createApiKey(@Valid @RequestBody CreateApiKeyRequest request) {
        try {
            // Get authenticated user
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || authentication.getName() == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            // Get user ID from authentication (set by JWT or API key filter)
            Long userId = Long.parseLong(authentication.getName());
            UserEntity user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            // Create API key
            ApiKeyService.CreateApiKeyResult result = apiKeyService.createApiKey(
                    user,
                    request.getName(),
                    request.getExpiry(),
                    request.getPermissions()
            );

            // Build response with plain API key (only shown once)
            CreateApiKeyResponse response = CreateApiKeyResponse.builder()
                    .apiKey(result.getPlainApiKey())
                    .expiresAt(result.getApiKeyEntity().getExpiresAt())
                    .build();

            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to create API key: " + e.getMessage()));
        }
    }

    @Operation(
            summary = "Rollover Expired API Key",
            description = "Creates a new API key to replace an expired one. Duplicates permissions from the old key. " +
                    "The old key must be expired (not revoked) to be eligible for rollover.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "API key rolled over successfully",
                    content = @Content(schema = @Schema(implementation = CreateApiKeyResponse.class))
            ),
            @ApiResponse(responseCode = "400", description = "Invalid request (key not found or not expired)"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "409", description = "Key is not expired or maximum active keys limit reached")
    })
    @PostMapping("/rollover")
    public ResponseEntity<?> rolloverApiKey(@Valid @RequestBody RolloverApiKeyRequest request) {
        try {
            // Get authenticated user
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || authentication.getName() == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            // Rollover the API key using expired key ID and new expiry
            ApiKeyService.CreateApiKeyResult result = apiKeyService.rolloverApiKey(
                    request.getExpiredKeyId(),
                    request.getExpiry()
            );

            // Build response with new API key
            CreateApiKeyResponse response = CreateApiKeyResponse.builder()
                    .apiKey(result.getPlainApiKey())
                    .expiresAt(result.getApiKeyEntity().getExpiresAt())
                    .build();

            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to rollover API key: " + e.getMessage()));
        }
    }
}


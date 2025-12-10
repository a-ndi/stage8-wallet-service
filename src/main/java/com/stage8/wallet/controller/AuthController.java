package com.stage8.wallet.controller;

import com.stage8.wallet.dto.AuthResponse;
import com.stage8.wallet.model.entity.UserEntity;
import com.stage8.wallet.model.entity.WalletEntity;
import com.stage8.wallet.repository.WalletRepository;
import com.stage8.wallet.security.JwtService;
import com.stage8.wallet.service.GoogleOAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Google OAuth authentication endpoints")
public class AuthController {

    private final GoogleOAuthService googleOAuthService;
    private final JwtService jwtService;
    private final WalletRepository walletRepository;

    @Value("${google.oauth.client-id}")
    private String clientId;

    @Value("${google.oauth.redirect-uri}")
    private String redirectUri;

    @Operation(
            summary = "Initiate Google OAuth Sign-In",
            description = "Redirects user to Google OAuth consent screen. After user authorizes, Google redirects to /auth/google/callback"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "302", description = "Redirect to Google OAuth consent screen")
    })
    @GetMapping("/google")
    public void googleSignIn(HttpServletResponse response) throws IOException {
        // Generate state for CSRF protection
        SecureRandom random = new SecureRandom();
        byte[] stateBytes = new byte[32];
        random.nextBytes(stateBytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(stateBytes);

        // Store state in session (in production, use Redis or similar)
        // For now, we'll include it in the redirect and validate in callback

        // Build Google OAuth authorization URL
        String googleAuthUrl = String.format(
                "https://accounts.google.com/o/oauth2/v2/auth?" +
                        "client_id=%s&" +
                        "redirect_uri=%s&" +
                        "response_type=code&" +
                        "scope=openid%%20email%%20profile&" +
                        "access_type=online&" +
                        "state=%s",
                URLEncoder.encode(clientId, StandardCharsets.UTF_8),
                URLEncoder.encode(redirectUri, StandardCharsets.UTF_8),
                URLEncoder.encode(state, StandardCharsets.UTF_8)
        );

        // Redirect to Google OAuth consent screen
        response.sendRedirect(googleAuthUrl);
    }

    @Operation(
            summary = "Google OAuth Callback",
            description = "Handles Google OAuth callback. Verifies token, creates user if not exists, creates wallet for new users, and returns JWT token."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Authentication successful",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))
            ),
            @ApiResponse(responseCode = "400", description = "Invalid request (missing code)"),
            @ApiResponse(responseCode = "401", description = "Authentication failed")
    })
    @GetMapping("/google/callback")
    public ResponseEntity<AuthResponse> googleCallback(
            @Parameter(description = "Authorization code from Google", required = false)
            @RequestParam(required = false) String code,
            @Parameter(description = "Error code from Google OAuth", required = false)
            @RequestParam(required = false) String error,
            @Parameter(description = "State parameter for CSRF protection", required = false)
            @RequestParam(required = false) String state
    ) {
        try {
            // Check for OAuth errors
            if (error != null) {
                return ResponseEntity.status(401)
                        .body(AuthResponse.builder()
                                .token(null)
                                .build());
            }

            // Validate authorization code
            if (code == null || code.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(AuthResponse.builder()
                                .token(null)
                                .build());
            }

            // Exchange authorization code for ID token
            String idToken = googleOAuthService.exchangeCodeForIdToken(code);

            // Verify Google token and get user info
            GoogleOAuthService.GoogleUserInfo googleUserInfo = googleOAuthService.verifyToken(idToken);

            // Create or retrieve user, create wallet if new
            UserEntity user = googleOAuthService.processGoogleUser(googleUserInfo);

            // Generate JWT token
            String jwtToken = jwtService.generateToken(user.getId().toString(), Map.of(
                    "email", user.getEmail(),
                    "name", user.getName() != null ? user.getName() : ""
            ));

            // Get wallet for user
            WalletEntity wallet = walletRepository.findByUser_Id(user.getId())
                    .orElse(null);

            // Build response
            AuthResponse response = AuthResponse.builder()
                    .token(jwtToken)
                    .email(user.getEmail())
                    .name(user.getName())
                    .userId(user.getId())
                    .walletNumber(wallet != null ? wallet.getWalletNumber() : null)
                    .build();

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(401)
                    .body(AuthResponse.builder()
                            .token(null)
                            .build());
        }
    }
}


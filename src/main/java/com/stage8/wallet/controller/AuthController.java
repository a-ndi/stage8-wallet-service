package com.stage8.wallet.controller;

import com.nimbusds.jwt.JWT;
import com.stage8.wallet.dto.AuthResponse;
import com.stage8.wallet.model.entity.UserEntity;
import com.stage8.wallet.model.entity.WalletEntity;
import com.stage8.wallet.repository.WalletRepository;
import com.stage8.wallet.security.JwtService;
import com.stage8.wallet.service.GoogleOAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final GoogleOAuthService googleOAuthService;
    private final JwtService jwtService;
    private final WalletRepository walletRepository;


     //Google OAuth callback endpoint
     //Verifies Google token, creates user/wallet if needed, and returns JWT

    @PostMapping("/google/callback")
    public ResponseEntity<AuthResponse> googleCallback(@RequestBody Map<String, String> request) {
        try {
            String idToken = request.get("idToken");
            if (idToken == null || idToken.isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            // Verify Google token
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
            return ResponseEntity.status(401).build();
        }
    }


     //Health check endpoint

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}


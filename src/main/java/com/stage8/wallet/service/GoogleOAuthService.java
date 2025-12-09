package com.stage8.wallet.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.stage8.wallet.model.entity.UserEntity;
import com.stage8.wallet.model.entity.WalletEntity;
import com.stage8.wallet.repository.UserRepository;
import com.stage8.wallet.repository.WalletRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Collections;

@Service
public class GoogleOAuthService {

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final GoogleIdTokenVerifier verifier;

    public GoogleOAuthService(UserRepository userRepository, 
                              WalletRepository walletRepository,
                              @Value("${google.oauth.client-id}") String clientId) {
        this.userRepository = userRepository;
        this.walletRepository = walletRepository;
        this.verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new GsonFactory())
                .setAudience(Collections.singletonList(clientId))
                .build();
    }

    //Verifies Google ID token and returns user information

    public GoogleUserInfo verifyToken(String idTokenString) throws Exception {
        GoogleIdToken idToken = verifier.verify(idTokenString);
        if (idToken == null) {
            throw new Exception("Invalid ID token");
        }

        //Extract payload (user data)
        GoogleIdToken.Payload payload = idToken.getPayload();
        String googleId = payload.getSubject();
        String email = payload.getEmail();
        String name = (String) payload.get("name");
        Boolean emailVerifiedObj = payload.getEmailVerified();
        boolean emailVerified = emailVerifiedObj != null && emailVerifiedObj;

        if (!emailVerified) {
            throw new Exception("Email not verified");
        }

        return new GoogleUserInfo(googleId, email, name);
    }


     //Creates or retrieves user, creates wallet if new user, and returns user entity

    @Transactional
    public UserEntity processGoogleUser(GoogleUserInfo googleUserInfo) {
        // Check if user exists by Google ID
        UserEntity user = userRepository.findByGoogleId(googleUserInfo.getGoogleId())
                .orElse(null);

        if (user == null) {
            // Check if user exists by email (in case Google ID changed)
            user = userRepository.findByEmail(googleUserInfo.getEmail())
                    .orElse(null);

            if (user != null) {
                // Update existing user with Google ID
                user.setGoogleId(googleUserInfo.getGoogleId());
                user.setName(googleUserInfo.getName());
                user = userRepository.save(user);
            } else {
                // Create new user
                user = UserEntity.builder()
                        .googleId(googleUserInfo.getGoogleId())
                        .email(googleUserInfo.getEmail())
                        .name(googleUserInfo.getName())
                        .build();
                user = userRepository.save(user);

                // Create wallet for new user
                createWalletForUser(user);
            }
        }

        return user;
    }


     //Creates a wallet for a user

    private void createWalletForUser(UserEntity user) {
        String walletNumber = generateWalletNumber();
        WalletEntity wallet = WalletEntity.builder()
                .user(user)
                .walletNumber(walletNumber)
                .build();
        walletRepository.save(wallet);
    }

    //Generates a unique wallet number

    private String generateWalletNumber() {
        SecureRandom random = new SecureRandom();
        String walletNumber;
        do {
            // Generate 10-digit wallet number
            walletNumber = String.format("%010d", random.nextInt(1000000000));
        } while (walletRepository.findByWalletNumber(walletNumber).isPresent());

        return walletNumber;
    }


      //Inner class to hold Google user information

    public static class GoogleUserInfo {
        private final String googleId;
        private final String email;
        private final String name;

        public GoogleUserInfo(String googleId, String email, String name) {
            this.googleId = googleId;
            this.email = email;
            this.name = name;
        }

        public String getGoogleId() {
            return googleId;
        }

        public String getEmail() {
            return email;
        }

        public String getName() {
            return name;
        }
    }
}


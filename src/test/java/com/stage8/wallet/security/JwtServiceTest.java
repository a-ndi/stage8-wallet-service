package com.stage8.wallet.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class JwtServiceTest {

    private JwtService jwtService;
    private static final String TEST_SECRET = "test-secret-key-that-is-at-least-256-bits-long-for-hmac-sha256";
    private static final Long TEST_EXPIRATION = 3600000L; // 1 hour

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secretKey", TEST_SECRET);
        ReflectionTestUtils.setField(jwtService, "expiration", TEST_EXPIRATION);
    }

    @Test
    void shouldGenerateTokenWithSubject() {
        // Given
        String userId = "12345";

        // When
        String token = jwtService.generateToken(userId);

        // Then
        assertThat(token).isNotNull();
        assertThat(token).isNotEmpty();
        String extractedSubject = jwtService.extractSubject(token);
        assertThat(extractedSubject).isEqualTo(userId);
    }

    @Test
    void shouldGenerateTokenWithClaims() {
        // Given
        String userId = "12345";
        Map<String, Object> claims = new HashMap<>();
        claims.put("email", "test@example.com");
        claims.put("role", "USER");

        // When
        String token = jwtService.generateToken(userId, claims);

        // Then
        assertThat(token).isNotNull();
        Claims extractedClaims = jwtService.extractAllClaims(token);
        assertThat(extractedClaims.getSubject()).isEqualTo(userId);
        assertThat(extractedClaims.get("email", String.class)).isEqualTo("test@example.com");
        assertThat(extractedClaims.get("role", String.class)).isEqualTo("USER");
    }

    @Test
    void shouldExtractSubjectFromValidToken() {
        // Given
        String userId = "67890";
        String token = jwtService.generateToken(userId);

        // When
        String extractedSubject = jwtService.extractSubject(token);

        // Then
        assertThat(extractedSubject).isEqualTo(userId);
    }

    @Test
    void shouldExtractSpecificClaim() {
        // Given
        String userId = "12345";
        Map<String, Object> claims = Map.of("customClaim", "customValue");
        String token = jwtService.generateToken(userId, claims);

        // When
        String customClaim = jwtService.extractClaim(token, c -> c.get("customClaim", String.class));

        // Then
        assertThat(customClaim).isEqualTo("customValue");
    }

    @Test
    void shouldValidateTokenSuccessfully() {
        // Given
        String token = jwtService.generateToken("12345");

        // When
        boolean isValid = jwtService.isTokenValid(token);

        // Then
        assertThat(isValid).isTrue();
    }

    @Test
    void shouldRejectExpiredToken() {
        // Given - create token with past expiration
        ReflectionTestUtils.setField(jwtService, "expiration", -1000L); // -1 second
        String expiredToken = jwtService.generateToken("12345");

        // Reset expiration for validation
        ReflectionTestUtils.setField(jwtService, "expiration", TEST_EXPIRATION);

        // When
        boolean isValid = jwtService.isTokenValid(expiredToken);

        // Then
        assertThat(isValid).isFalse();
    }

    @Test
    void shouldRejectTokenWithInvalidSignature() {
        // Given - create token with one secret
        String token = jwtService.generateToken("12345");

        // Change the secret key
        ReflectionTestUtils.setField(jwtService, "secretKey", "different-secret-key-that-is-at-least-256-bits-long-for-hmac");

        // When
        boolean isValid = jwtService.isTokenValid(token);

        // Then
        assertThat(isValid).isFalse();
    }

    @Test
    void shouldExtractExpirationDate() {
        // Given
        String token = jwtService.generateToken("12345");

        // When
        Date expiration = jwtService.extractExpiration(token);

        // Then
        assertThat(expiration).isNotNull();
        assertThat(expiration.getTime()).isGreaterThan(System.currentTimeMillis());
    }

    @Test
    void shouldThrowExceptionForMalformedToken() {
        // Given
        String malformedToken = "this.is.not.a.valid.jwt";

        // When/Then
        assertThatThrownBy(() -> jwtService.extractSubject(malformedToken))
                .isInstanceOf(Exception.class);
    }

    @Test
    void shouldHandleNullToken() {
        // Given
        String nullToken = null;

        // When/Then
        assertThatThrownBy(() -> jwtService.extractSubject(nullToken))
                .isInstanceOf(Exception.class);
    }

    @Test
    void shouldExtractAllClaims() {
        // Given
        String userId = "12345";
        Map<String, Object> claims = new HashMap<>();
        claims.put("email", "test@example.com");
        claims.put("role", "ADMIN");
        String token = jwtService.generateToken(userId, claims);

        // When
        Claims extractedClaims = jwtService.extractAllClaims(token);

        // Then
        assertThat(extractedClaims).isNotNull();
        assertThat(extractedClaims.getSubject()).isEqualTo(userId);
        assertThat(extractedClaims.get("email")).isEqualTo("test@example.com");
        assertThat(extractedClaims.get("role")).isEqualTo("ADMIN");
        assertThat(extractedClaims.getIssuedAt()).isNotNull();
        assertThat(extractedClaims.getExpiration()).isNotNull();
    }

    @Test
    void shouldReturnFalseForInvalidTokenFormat() {
        // Given
        String invalidToken = "invalid-token-format";

        // When
        boolean isValid = jwtService.isTokenValid(invalidToken);

        // Then
        assertThat(isValid).isFalse();
    }

    @Test
    void shouldGenerateUniqueTokensForSameClaims() throws InterruptedException {
        // Given
        String userId = "12345";

        // When
        String token1 = jwtService.generateToken(userId);
        Thread.sleep(1000); // Ensure different issuedAt time (1 second)
        String token2 = jwtService.generateToken(userId);

        // Then
        assertThat(token1).isNotEqualTo(token2);
    }
}

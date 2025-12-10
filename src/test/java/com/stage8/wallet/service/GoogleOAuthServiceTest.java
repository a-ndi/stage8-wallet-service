package com.stage8.wallet.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.stage8.wallet.model.entity.UserEntity;
import com.stage8.wallet.model.entity.WalletEntity;
import com.stage8.wallet.repository.UserRepository;
import com.stage8.wallet.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GoogleOAuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private GoogleIdTokenVerifier verifier;

    private GoogleOAuthService googleOAuthService;

    @BeforeEach
    void setUp() {
        // Create service with all required constructor parameters
        googleOAuthService = new GoogleOAuthService(
                userRepository,
                walletRepository,
                "test-client-id",
                "test-client-secret",
                "http://localhost:8080/auth/google/callback"
        );
        // Inject mock verifier
        ReflectionTestUtils.setField(googleOAuthService, "verifier", verifier);
    }

    @Test
    void shouldVerifyValidGoogleToken() throws Exception {
        // Given
        String idTokenString = "valid-google-id-token";
        GoogleIdToken mockIdToken = mock(GoogleIdToken.class);
        GoogleIdToken.Payload mockPayload = mock(GoogleIdToken.Payload.class);

        when(verifier.verify(idTokenString)).thenReturn(mockIdToken);
        when(mockIdToken.getPayload()).thenReturn(mockPayload);
        when(mockPayload.getSubject()).thenReturn("google-user-123");
        when(mockPayload.getEmail()).thenReturn("test@example.com");
        when(mockPayload.get("name")).thenReturn("Test User");
        when(mockPayload.getEmailVerified()).thenReturn(true);

        // When
        GoogleOAuthService.GoogleUserInfo userInfo = googleOAuthService.verifyToken(idTokenString);

        // Then
        assertThat(userInfo).isNotNull();
        assertThat(userInfo.getGoogleId()).isEqualTo("google-user-123");
        assertThat(userInfo.getEmail()).isEqualTo("test@example.com");
        assertThat(userInfo.getName()).isEqualTo("Test User");
        verify(verifier).verify(idTokenString);
    }

    @Test
    void shouldRejectInvalidGoogleToken() throws Exception {
        // Given
        String invalidToken = "invalid-token";
        when(verifier.verify(invalidToken)).thenReturn(null);

        // When/Then
        assertThatThrownBy(() -> googleOAuthService.verifyToken(invalidToken))
                .isInstanceOf(Exception.class)
                .hasMessageContaining("Invalid ID token");
    }

    @Test
    void shouldRejectUnverifiedEmail() throws Exception {
        // Given
        String idTokenString = "token-with-unverified-email";
        GoogleIdToken mockIdToken = mock(GoogleIdToken.class);
        GoogleIdToken.Payload mockPayload = mock(GoogleIdToken.Payload.class);

        when(verifier.verify(idTokenString)).thenReturn(mockIdToken);
        when(mockIdToken.getPayload()).thenReturn(mockPayload);
        when(mockPayload.getSubject()).thenReturn("google-user-123");
        when(mockPayload.getEmail()).thenReturn("test@example.com");
        when(mockPayload.get("name")).thenReturn("Test User");
        when(mockPayload.getEmailVerified()).thenReturn(false);

        // When/Then
        assertThatThrownBy(() -> googleOAuthService.verifyToken(idTokenString))
                .isInstanceOf(Exception.class)
                .hasMessageContaining("Email not verified");
    }

    @Test
    void shouldCreateNewUserWhenNotExists() {
        // Given
        GoogleOAuthService.GoogleUserInfo googleUserInfo =
                new GoogleOAuthService.GoogleUserInfo("google-123", "new@example.com", "New User");

        UserEntity savedUser = UserEntity.builder()
                .id(1L)
                .googleId("google-123")
                .email("new@example.com")
                .name("New User")
                .build();

        when(userRepository.findByGoogleId("google-123")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(UserEntity.class))).thenReturn(savedUser);
        when(walletRepository.findByWalletNumber(anyString())).thenReturn(Optional.empty());
        when(walletRepository.save(any(WalletEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        UserEntity result = googleOAuthService.processGoogleUser(googleUserInfo);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getGoogleId()).isEqualTo("google-123");
        assertThat(result.getEmail()).isEqualTo("new@example.com");
        assertThat(result.getName()).isEqualTo("New User");

        verify(userRepository).findByGoogleId("google-123");
        verify(userRepository).findByEmail("new@example.com");
        verify(userRepository).save(any(UserEntity.class));
        verify(walletRepository).save(any(WalletEntity.class));
    }

    @Test
    void shouldReturnExistingUserByGoogleId() {
        // Given
        GoogleOAuthService.GoogleUserInfo googleUserInfo =
                new GoogleOAuthService.GoogleUserInfo("google-123", "existing@example.com", "Existing User");

        UserEntity existingUser = UserEntity.builder()
                .id(1L)
                .googleId("google-123")
                .email("existing@example.com")
                .name("Existing User")
                .build();

        when(userRepository.findByGoogleId("google-123")).thenReturn(Optional.of(existingUser));

        // When
        UserEntity result = googleOAuthService.processGoogleUser(googleUserInfo);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getGoogleId()).isEqualTo("google-123");

        verify(userRepository).findByGoogleId("google-123");
        verify(userRepository, never()).save(any());
        verify(walletRepository, never()).save(any());
    }

    @Test
    void shouldReturnExistingUserByEmailAndUpdateGoogleId() {
        // Given
        GoogleOAuthService.GoogleUserInfo googleUserInfo =
                new GoogleOAuthService.GoogleUserInfo("new-google-id", "existing@example.com", "Updated User");

        UserEntity existingUser = UserEntity.builder()
                .id(1L)
                .email("existing@example.com")
                .name("Old Name")
                .build();

        UserEntity updatedUser = UserEntity.builder()
                .id(1L)
                .googleId("new-google-id")
                .email("existing@example.com")
                .name("Updated User")
                .build();

        when(userRepository.findByGoogleId("new-google-id")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("existing@example.com")).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(UserEntity.class))).thenReturn(updatedUser);

        // When
        UserEntity result = googleOAuthService.processGoogleUser(googleUserInfo);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getGoogleId()).isEqualTo("new-google-id");
        assertThat(result.getName()).isEqualTo("Updated User");

        verify(userRepository).findByGoogleId("new-google-id");
        verify(userRepository).findByEmail("existing@example.com");
        verify(userRepository).save(any(UserEntity.class));
        verify(walletRepository, never()).save(any());
    }

    @Test
    void shouldCreateWalletForNewUser() {
        // Given
        GoogleOAuthService.GoogleUserInfo googleUserInfo =
                new GoogleOAuthService.GoogleUserInfo("google-123", "new@example.com", "New User");

        UserEntity savedUser = UserEntity.builder()
                .id(1L)
                .googleId("google-123")
                .email("new@example.com")
                .name("New User")
                .build();

        when(userRepository.findByGoogleId("google-123")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(UserEntity.class))).thenReturn(savedUser);
        when(walletRepository.findByWalletNumber(anyString())).thenReturn(Optional.empty());

        ArgumentCaptor<WalletEntity> walletCaptor = ArgumentCaptor.forClass(WalletEntity.class);

        // When
        googleOAuthService.processGoogleUser(googleUserInfo);

        // Then
        verify(walletRepository).save(walletCaptor.capture());
        WalletEntity capturedWallet = walletCaptor.getValue();

        assertThat(capturedWallet).isNotNull();
        assertThat(capturedWallet.getUser()).isEqualTo(savedUser);
        assertThat(capturedWallet.getWalletNumber()).isNotNull();
        assertThat(capturedWallet.getWalletNumber()).hasSize(10);
        assertThat(capturedWallet.getWalletNumber()).containsOnlyDigits();
    }

    @Test
    void shouldNotCreateDuplicateWallet() {
        // Given
        GoogleOAuthService.GoogleUserInfo googleUserInfo =
                new GoogleOAuthService.GoogleUserInfo("google-123", "existing@example.com", "Existing User");

        UserEntity existingUser = UserEntity.builder()
                .id(1L)
                .googleId("google-123")
                .email("existing@example.com")
                .name("Existing User")
                .build();

        when(userRepository.findByGoogleId("google-123")).thenReturn(Optional.of(existingUser));

        // When
        googleOAuthService.processGoogleUser(googleUserInfo);

        // Then
        verify(walletRepository, never()).save(any());
    }

    @Test
    void shouldGenerateUniqueWalletNumber() {
        // Given
        GoogleOAuthService.GoogleUserInfo googleUserInfo =
                new GoogleOAuthService.GoogleUserInfo("google-123", "new@example.com", "New User");

        UserEntity savedUser = UserEntity.builder()
                .id(1L)
                .googleId("google-123")
                .email("new@example.com")
                .name("New User")
                .build();

        when(userRepository.findByGoogleId("google-123")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(UserEntity.class))).thenReturn(savedUser);
        when(walletRepository.findByWalletNumber(anyString())).thenReturn(Optional.empty());

        ArgumentCaptor<WalletEntity> walletCaptor = ArgumentCaptor.forClass(WalletEntity.class);

        // When
        googleOAuthService.processGoogleUser(googleUserInfo);

        // Then
        verify(walletRepository).save(walletCaptor.capture());
        String walletNumber = walletCaptor.getValue().getWalletNumber();

        assertThat(walletNumber).matches("\\d{10}");
    }

    @Test
    void shouldRetryWalletNumberGenerationOnCollision() {
        // Given
        GoogleOAuthService.GoogleUserInfo googleUserInfo =
                new GoogleOAuthService.GoogleUserInfo("google-123", "new@example.com", "New User");

        UserEntity savedUser = UserEntity.builder()
                .id(1L)
                .googleId("google-123")
                .email("new@example.com")
                .name("New User")
                .build();

        WalletEntity existingWallet = WalletEntity.builder()
                .walletNumber("1234567890")
                .build();

        when(userRepository.findByGoogleId("google-123")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(UserEntity.class))).thenReturn(savedUser);

        // First call returns existing wallet (collision), second returns empty (unique)
        when(walletRepository.findByWalletNumber(anyString()))
                .thenReturn(Optional.of(existingWallet))
                .thenReturn(Optional.empty());

        // When
        googleOAuthService.processGoogleUser(googleUserInfo);

        // Then
        verify(walletRepository, atLeast(2)).findByWalletNumber(anyString());
        verify(walletRepository).save(any(WalletEntity.class));
    }

    @Test
    void shouldHandleNullEmailVerified() throws Exception {
        // Given
        String idTokenString = "token-with-null-email-verified";
        GoogleIdToken mockIdToken = mock(GoogleIdToken.class);
        GoogleIdToken.Payload mockPayload = mock(GoogleIdToken.Payload.class);

        when(verifier.verify(idTokenString)).thenReturn(mockIdToken);
        when(mockIdToken.getPayload()).thenReturn(mockPayload);
        when(mockPayload.getSubject()).thenReturn("google-user-123");
        when(mockPayload.getEmail()).thenReturn("test@example.com");
        when(mockPayload.get("name")).thenReturn("Test User");
        when(mockPayload.getEmailVerified()).thenReturn(null);

        // When/Then
        assertThatThrownBy(() -> googleOAuthService.verifyToken(idTokenString))
                .isInstanceOf(Exception.class)
                .hasMessageContaining("Email not verified");
    }
}

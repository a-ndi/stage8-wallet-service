package com.stage8.wallet.controller;

import com.stage8.wallet.dto.AuthResponse;
import com.stage8.wallet.model.entity.UserEntity;
import com.stage8.wallet.model.entity.WalletEntity;
import com.stage8.wallet.repository.WalletRepository;
import com.stage8.wallet.security.JwtService;
import com.stage8.wallet.service.GoogleOAuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private GoogleOAuthService googleOAuthService;

    @Mock
    private JwtService jwtService;

    @Mock
    private WalletRepository walletRepository;

    @InjectMocks
    private AuthController authController;

    private UserEntity testUser;
    private WalletEntity testWallet;
    private GoogleOAuthService.GoogleUserInfo testGoogleUserInfo;

    @BeforeEach
    void setUp() {
        testUser = UserEntity.builder()
                .id(1L)
                .googleId("google-123")
                .email("test@example.com")
                .name("Test User")
                .build();

        testWallet = WalletEntity.builder()
                .id(1L)
                .user(testUser)
                .walletNumber("1234567890")
                .build();

        testGoogleUserInfo = new GoogleOAuthService.GoogleUserInfo(
                "google-123", "test@example.com", "Test User");
    }

    @Test
    void shouldReturnJwtOnSuccessfulGoogleCallback() throws Exception {
        // Given
        String code = "valid-authorization-code";
        String idToken = "valid-google-id-token";
        String jwtToken = "generated.jwt.token";

        when(googleOAuthService.exchangeCodeForIdToken(code)).thenReturn(idToken);
        when(googleOAuthService.verifyToken(idToken)).thenReturn(testGoogleUserInfo);
        when(googleOAuthService.processGoogleUser(testGoogleUserInfo)).thenReturn(testUser);
        when(jwtService.generateToken(eq("1"), anyMap())).thenReturn(jwtToken);
        when(walletRepository.findByUser_Id(1L)).thenReturn(Optional.of(testWallet));

        // When
        ResponseEntity<AuthResponse> response = authController.googleCallback(code, null, null);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getToken()).isEqualTo(jwtToken);
        assertThat(response.getBody().getEmail()).isEqualTo("test@example.com");
        assertThat(response.getBody().getName()).isEqualTo("Test User");
        assertThat(response.getBody().getUserId()).isEqualTo(1L);
        assertThat(response.getBody().getWalletNumber()).isEqualTo("1234567890");

        verify(googleOAuthService).exchangeCodeForIdToken(code);
        verify(googleOAuthService).verifyToken(idToken);
        verify(googleOAuthService).processGoogleUser(testGoogleUserInfo);
        verify(jwtService).generateToken(eq("1"), anyMap());
    }

    @Test
    void shouldReturn400OnMissingCode() {
        // When
        ResponseEntity<AuthResponse> response = authController.googleCallback(null, null, null);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void shouldReturn400OnEmptyCode() {
        // When
        ResponseEntity<AuthResponse> response = authController.googleCallback("", null, null);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void shouldReturn401OnOAuthError() {
        // When
        ResponseEntity<AuthResponse> response = authController.googleCallback(null, "access_denied", null);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldReturn401OnInvalidGoogleToken() throws Exception {
        // Given
        String code = "valid-code";
        String idToken = "invalid-google-token";

        when(googleOAuthService.exchangeCodeForIdToken(code)).thenReturn(idToken);
        when(googleOAuthService.verifyToken(idToken))
                .thenThrow(new Exception("Invalid ID token"));

        // When
        ResponseEntity<AuthResponse> response = authController.googleCallback(code, null, null);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(googleOAuthService).exchangeCodeForIdToken(code);
        verify(googleOAuthService).verifyToken(idToken);
    }

    @Test
    void shouldCreateUserAndWalletForNewGoogleUser() throws Exception {
        // Given
        String code = "new-user-code";
        String idToken = "new-user-google-token";
        String jwtToken = "new.jwt.token";

        UserEntity newUser = UserEntity.builder()
                .id(2L)
                .googleId("new-google-123")
                .email("newuser@example.com")
                .name("New User")
                .build();

        WalletEntity newWallet = WalletEntity.builder()
                .id(2L)
                .user(newUser)
                .walletNumber("9876543210")
                .build();

        GoogleOAuthService.GoogleUserInfo newGoogleUserInfo =
                new GoogleOAuthService.GoogleUserInfo("new-google-123", "newuser@example.com", "New User");

        when(googleOAuthService.exchangeCodeForIdToken(code)).thenReturn(idToken);
        when(googleOAuthService.verifyToken(idToken)).thenReturn(newGoogleUserInfo);
        when(googleOAuthService.processGoogleUser(newGoogleUserInfo)).thenReturn(newUser);
        when(jwtService.generateToken(eq("2"), anyMap())).thenReturn(jwtToken);
        when(walletRepository.findByUser_Id(2L)).thenReturn(Optional.of(newWallet));

        // When
        ResponseEntity<AuthResponse> response = authController.googleCallback(code, null, null);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getToken()).isEqualTo(jwtToken);
        assertThat(response.getBody().getEmail()).isEqualTo("newuser@example.com");
        assertThat(response.getBody().getUserId()).isEqualTo(2L);
        assertThat(response.getBody().getWalletNumber()).isEqualTo("9876543210");

        verify(googleOAuthService).processGoogleUser(newGoogleUserInfo);
    }

    @Test
    void shouldReturnUserDataWithAllFields() throws Exception {
        // Given
        String code = "valid-code";
        String idToken = "valid-token";
        String jwtToken = "detail.jwt.token";

        when(googleOAuthService.exchangeCodeForIdToken(code)).thenReturn(idToken);
        when(googleOAuthService.verifyToken(idToken)).thenReturn(testGoogleUserInfo);
        when(googleOAuthService.processGoogleUser(testGoogleUserInfo)).thenReturn(testUser);
        when(jwtService.generateToken(eq("1"), anyMap())).thenReturn(jwtToken);
        when(walletRepository.findByUser_Id(1L)).thenReturn(Optional.of(testWallet));

        // When
        ResponseEntity<AuthResponse> response = authController.googleCallback(code, null, null);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        AuthResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getToken()).isNotNull();
        assertThat(body.getEmail()).isNotNull();
        assertThat(body.getName()).isNotNull();
        assertThat(body.getUserId()).isNotNull();
        assertThat(body.getWalletNumber()).isNotNull();
    }

    @Test
    void shouldHandleUserWithNullName() throws Exception {
        // Given
        String code = "code-with-null-name";
        String idToken = "token-with-null-name";
        String jwtToken = "noname.jwt.token";

        UserEntity userWithNullName = UserEntity.builder()
                .id(5L)
                .googleId("google-789")
                .email("noname@example.com")
                .name(null)
                .build();

        WalletEntity wallet = WalletEntity.builder()
                .id(5L)
                .user(userWithNullName)
                .walletNumber("2222222222")
                .build();

        GoogleOAuthService.GoogleUserInfo googleUserInfo =
                new GoogleOAuthService.GoogleUserInfo("google-789", "noname@example.com", null);

        when(googleOAuthService.exchangeCodeForIdToken(code)).thenReturn(idToken);
        when(googleOAuthService.verifyToken(idToken)).thenReturn(googleUserInfo);
        when(googleOAuthService.processGoogleUser(googleUserInfo)).thenReturn(userWithNullName);
        when(jwtService.generateToken(eq("5"), anyMap())).thenReturn(jwtToken);
        when(walletRepository.findByUser_Id(5L)).thenReturn(Optional.of(wallet));

        // When
        ResponseEntity<AuthResponse> response = authController.googleCallback(code, null, null);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getToken()).isEqualTo(jwtToken);
        assertThat(response.getBody().getEmail()).isEqualTo("noname@example.com");
    }

    @Test
    void shouldHandleUserWithoutWallet() throws Exception {
        // Given
        String code = "code-without-wallet";
        String idToken = "token-without-wallet";
        String jwtToken = "nowallet.jwt.token";

        UserEntity userWithoutWallet = UserEntity.builder()
                .id(6L)
                .googleId("google-999")
                .email("nowallet@example.com")
                .name("No Wallet User")
                .build();

        GoogleOAuthService.GoogleUserInfo googleUserInfo =
                new GoogleOAuthService.GoogleUserInfo("google-999", "nowallet@example.com", "No Wallet User");

        when(googleOAuthService.exchangeCodeForIdToken(code)).thenReturn(idToken);
        when(googleOAuthService.verifyToken(idToken)).thenReturn(googleUserInfo);
        when(googleOAuthService.processGoogleUser(googleUserInfo)).thenReturn(userWithoutWallet);
        when(jwtService.generateToken(eq("6"), anyMap())).thenReturn(jwtToken);
        when(walletRepository.findByUser_Id(6L)).thenReturn(Optional.empty());

        // When
        ResponseEntity<AuthResponse> response = authController.googleCallback(code, null, null);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getToken()).isEqualTo(jwtToken);
        assertThat(response.getBody().getWalletNumber()).isNull();
    }

    @Test
    void shouldReturn401WhenGoogleOAuthServiceThrowsRuntimeException() throws Exception {
        // Given
        String code = "exception-code";

        when(googleOAuthService.exchangeCodeForIdToken(code))
                .thenThrow(new RuntimeException("Service unavailable"));

        // When
        ResponseEntity<AuthResponse> response = authController.googleCallback(code, null, null);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}

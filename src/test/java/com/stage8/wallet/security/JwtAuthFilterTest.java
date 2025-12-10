package com.stage8.wallet.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private JwtAuthFilter jwtAuthFilter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldAuthenticateValidBearerToken() throws ServletException, IOException {
        // Given
        String validToken = "valid.jwt.token";
        String userId = "12345";

        when(request.getHeader("Authorization")).thenReturn("Bearer " + validToken);
        when(jwtService.extractSubject(validToken)).thenReturn(userId);
        when(jwtService.isTokenValid(validToken)).thenReturn(true);

        // When
        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        // Then
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication())
                .isInstanceOf(UsernamePasswordAuthenticationToken.class);
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .isEqualTo(userId);

        verify(jwtService).extractSubject(validToken);
        verify(jwtService).isTokenValid(validToken);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldSkipAuthenticationWhenNoAuthHeader() throws ServletException, IOException {
        // Given
        when(request.getHeader("Authorization")).thenReturn(null);

        // When
        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        // Then
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(jwtService, never()).extractSubject(anyString());
        verify(jwtService, never()).isTokenValid(anyString());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldSkipAuthenticationWhenNotBearerToken() throws ServletException, IOException {
        // Given
        when(request.getHeader("Authorization")).thenReturn("Basic username:password");

        // When
        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        // Then
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(jwtService, never()).extractSubject(anyString());
        verify(jwtService, never()).isTokenValid(anyString());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldRejectInvalidToken() throws ServletException, IOException {
        // Given
        String invalidToken = "invalid.jwt.token";

        when(request.getHeader("Authorization")).thenReturn("Bearer " + invalidToken);
        when(jwtService.extractSubject(invalidToken)).thenReturn("12345");
        when(jwtService.isTokenValid(invalidToken)).thenReturn(false);

        // When
        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        // Then
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(jwtService).extractSubject(invalidToken);
        verify(jwtService).isTokenValid(invalidToken);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldRejectExpiredToken() throws ServletException, IOException {
        // Given
        String expiredToken = "expired.jwt.token";

        when(request.getHeader("Authorization")).thenReturn("Bearer " + expiredToken);
        when(jwtService.extractSubject(expiredToken)).thenReturn("12345");
        when(jwtService.isTokenValid(expiredToken)).thenReturn(false);

        // When
        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        // Then
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldExtractUserIdFromToken() throws ServletException, IOException {
        // Given
        String token = "valid.jwt.token";
        String expectedUserId = "67890";

        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtService.extractSubject(token)).thenReturn(expectedUserId);
        when(jwtService.isTokenValid(token)).thenReturn(true);

        // When
        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        // Then
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .isEqualTo(expectedUserId);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldContinueFilterChainAfterAuthentication() throws ServletException, IOException {
        // Given
        String token = "valid.jwt.token";

        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtService.extractSubject(token)).thenReturn("12345");
        when(jwtService.isTokenValid(token)).thenReturn(true);

        // When
        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        // Then
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldHandleExceptionsGracefully() throws ServletException, IOException {
        // Given
        String malformedToken = "malformed.token";

        when(request.getHeader("Authorization")).thenReturn("Bearer " + malformedToken);
        when(jwtService.extractSubject(malformedToken)).thenThrow(new RuntimeException("JWT parsing failed"));

        // When
        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        // Then
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldNotAuthenticateWhenSubjectIsNull() throws ServletException, IOException {
        // Given
        String token = "token.with.null.subject";

        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtService.extractSubject(token)).thenReturn(null);

        // When
        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        // Then
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(jwtService).extractSubject(token);
        verify(jwtService, never()).isTokenValid(anyString());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldNotOverrideExistingAuthentication() throws ServletException, IOException {
        // Given
        String token = "valid.jwt.token";
        UsernamePasswordAuthenticationToken existingAuth =
                new UsernamePasswordAuthenticationToken("existingUser", null, null);
        SecurityContextHolder.getContext().setAuthentication(existingAuth);

        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtService.extractSubject(token)).thenReturn("12345");
        // Don't mock isTokenValid as it won't be called due to existing auth

        // When
        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        // Then
        assertThat(SecurityContextHolder.getContext().getAuthentication())
                .isEqualTo(existingAuth);
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .isEqualTo("existingUser");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldHandleEmptyBearerToken() throws ServletException, IOException {
        // Given
        when(request.getHeader("Authorization")).thenReturn("Bearer ");

        // When
        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        // Then
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldHandleWhitespaceInBearerToken() throws ServletException, IOException {
        // Given
        String token = "valid.jwt.token";

        when(request.getHeader("Authorization")).thenReturn("Bearer  " + token);
        when(jwtService.extractSubject(" " + token)).thenThrow(new RuntimeException("Invalid token format"));

        // When
        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        // Then
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldSetAuthenticationDetailsFromRequest() throws ServletException, IOException {
        // Given
        String token = "valid.jwt.token";
        String userId = "12345";

        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtService.extractSubject(token)).thenReturn(userId);
        when(jwtService.isTokenValid(token)).thenReturn(true);

        // When
        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        // Then
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getDetails()).isNotNull();
        verify(filterChain).doFilter(request, response);
    }
}

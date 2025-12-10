package com.stage8.wallet.repository;

import com.stage8.wallet.model.entity.ApiKeyEntity;
import com.stage8.wallet.model.entity.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import com.stage8.wallet.model.enums.Permission;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class ApiKeyRepositoryTest {

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager entityManager;

    private UserEntity testUser;

    @BeforeEach
    void setUp() {
        testUser = UserEntity.builder()
                .email("test@example.com")
                .name("Test User")
                .googleId("google-123")
                .build();
        testUser = entityManager.persistAndFlush(testUser);
    }

    private Set<Permission> defaultPermissions() {
        return EnumSet.of(Permission.READ, Permission.DEPOSIT);
    }

    @Test
    void shouldFindApiKeyByKeyHash() {
        // Given
        ApiKeyEntity apiKey = ApiKeyEntity.builder()
                .keyHash("hashed-key-123")
                .owner(testUser)
                .name("Test Key")
                .permissions(defaultPermissions())
                .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS))
                .revoked(false)
                .build();
        entityManager.persistAndFlush(apiKey);

        // When
        Optional<ApiKeyEntity> foundKey = apiKeyRepository.findByKeyHash("hashed-key-123");

        // Then
        assertThat(foundKey).isPresent();
        assertThat(foundKey.get().getKeyHash()).isEqualTo("hashed-key-123");
        assertThat(foundKey.get().getOwner().getId()).isEqualTo(testUser.getId());
    }

    @Test
    void shouldReturnEmptyWhenHashNotFound() {
        // When
        Optional<ApiKeyEntity> foundKey = apiKeyRepository.findByKeyHash("nonexistent-hash");

        // Then
        assertThat(foundKey).isEmpty();
    }

    @Test
    void shouldFindNonRevokedKeysByOwnerId() {
        // Given
        ApiKeyEntity activeKey1 = ApiKeyEntity.builder()
                .keyHash("active-key-1")
                .owner(testUser)
                .name("Test Key")
                .permissions(defaultPermissions())
                .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS))
                .revoked(false)
                .build();

        ApiKeyEntity activeKey2 = ApiKeyEntity.builder()
                .keyHash("active-key-2")
                .owner(testUser)
                .name("Test Key")
                .permissions(defaultPermissions())
                .expiresAt(Instant.now().plus(2, ChronoUnit.DAYS))
                .revoked(false)
                .build();

        ApiKeyEntity revokedKey = ApiKeyEntity.builder()
                .keyHash("revoked-key")
                .owner(testUser)
                .name("Test Key")
                .permissions(defaultPermissions())
                .expiresAt(Instant.now().plus(3, ChronoUnit.DAYS))
                .revoked(true)
                .build();

        entityManager.persistAndFlush(activeKey1);
        entityManager.persistAndFlush(activeKey2);
        entityManager.persistAndFlush(revokedKey);

        // When
        List<ApiKeyEntity> activeKeys = apiKeyRepository.findByOwnerIdAndRevokedFalse(testUser.getId());

        // Then
        assertThat(activeKeys).hasSize(2);
        assertThat(activeKeys)
                .extracting(ApiKeyEntity::getKeyHash)
                .containsExactlyInAnyOrder("active-key-1", "active-key-2");
        assertThat(activeKeys)
                .allMatch(key -> !key.isRevoked());
    }

    @Test
    void shouldNotReturnRevokedKeys() {
        // Given
        ApiKeyEntity revokedKey1 = ApiKeyEntity.builder()
                .keyHash("revoked-key-1")
                .owner(testUser)
                .name("Test Key")
                .permissions(defaultPermissions())
                .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS))
                .revoked(true)
                .build();

        ApiKeyEntity revokedKey2 = ApiKeyEntity.builder()
                .keyHash("revoked-key-2")
                .owner(testUser)
                .name("Test Key")
                .permissions(defaultPermissions())
                .expiresAt(Instant.now().plus(2, ChronoUnit.DAYS))
                .revoked(true)
                .build();

        entityManager.persistAndFlush(revokedKey1);
        entityManager.persistAndFlush(revokedKey2);

        // When
        List<ApiKeyEntity> activeKeys = apiKeyRepository.findByOwnerIdAndRevokedFalse(testUser.getId());

        // Then
        assertThat(activeKeys).isEmpty();
    }

    @Test
    void shouldReturnEmptyListWhenUserHasNoKeys() {
        // Given
        UserEntity userWithoutKeys = UserEntity.builder()
                .email("nokeys@example.com")
                .name("No Keys User")
                .googleId("google-nokeys")
                .build();
        userWithoutKeys = entityManager.persistAndFlush(userWithoutKeys);

        // When
        List<ApiKeyEntity> keys = apiKeyRepository.findByOwnerIdAndRevokedFalse(userWithoutKeys.getId());

        // Then
        assertThat(keys).isEmpty();
    }

    @Test
    void shouldSaveApiKeySuccessfully() {
        // Given
        ApiKeyEntity apiKey = ApiKeyEntity.builder()
                .keyHash("new-key-hash")
                .owner(testUser)
                .name("Test Key")
                .permissions(defaultPermissions())
                .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS))
                .revoked(false)
                .build();

        // When
        ApiKeyEntity savedKey = apiKeyRepository.save(apiKey);
        entityManager.flush();

        // Then
        assertThat(savedKey).isNotNull();
        assertThat(savedKey.getId()).isNotNull();
        assertThat(savedKey.getKeyHash()).isEqualTo("new-key-hash");
        assertThat(savedKey.getCreatedAt()).isNotNull();
        assertThat(savedKey.isRevoked()).isFalse();
    }

    @Test
    void shouldAutoSetCreatedAtOnPersist() {
        // Given
        ApiKeyEntity apiKey = ApiKeyEntity.builder()
                .keyHash("auto-timestamp-key")
                .owner(testUser)
                .name("Test Key")
                .permissions(defaultPermissions())
                .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS))
                .build();

        // When
        ApiKeyEntity savedKey = apiKeyRepository.save(apiKey);
        entityManager.flush();

        // Then
        assertThat(savedKey.getCreatedAt()).isNotNull();
        assertThat(savedKey.getCreatedAt()).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    void shouldUpdateApiKeyRevocationStatus() {
        // Given
        ApiKeyEntity apiKey = ApiKeyEntity.builder()
                .keyHash("revoke-me-key")
                .owner(testUser)
                .name("Test Key")
                .permissions(defaultPermissions())
                .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS))
                .revoked(false)
                .build();
        ApiKeyEntity savedKey = entityManager.persistAndFlush(apiKey);

        // When
        savedKey.setRevoked(true);
        ApiKeyEntity updatedKey = apiKeyRepository.save(savedKey);
        entityManager.flush();

        // Then
        assertThat(updatedKey.isRevoked()).isTrue();
        assertThat(updatedKey.getId()).isEqualTo(savedKey.getId());
    }

    @Test
    void shouldDeleteApiKey() {
        // Given
        ApiKeyEntity apiKey = ApiKeyEntity.builder()
                .keyHash("delete-me-key")
                .owner(testUser)
                .name("Test Key")
                .permissions(defaultPermissions())
                .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS))
                .revoked(false)
                .build();
        ApiKeyEntity savedKey = entityManager.persistAndFlush(apiKey);
        Long keyId = savedKey.getId();

        // When
        apiKeyRepository.delete(savedKey);
        entityManager.flush();

        // Then
        Optional<ApiKeyEntity> foundKey = apiKeyRepository.findById(keyId);
        assertThat(foundKey).isEmpty();
    }

    @Test
    void shouldReturnOnlyKeysForCorrectOwner() {
        // Given
        UserEntity otherUser = UserEntity.builder()
                .email("other@example.com")
                .name("Other User")
                .googleId("google-other")
                .build();
        otherUser = entityManager.persistAndFlush(otherUser);

        ApiKeyEntity testUserKey = ApiKeyEntity.builder()
                .keyHash("test-user-key")
                .owner(testUser)
                .name("Test Key")
                .permissions(defaultPermissions())
                .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS))
                .revoked(false)
                .build();

        ApiKeyEntity otherUserKey = ApiKeyEntity.builder()
                .keyHash("other-user-key")
                .owner(otherUser)
                .name("Test Key")
                .permissions(defaultPermissions())
                .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS))
                .revoked(false)
                .build();

        entityManager.persistAndFlush(testUserKey);
        entityManager.persistAndFlush(otherUserKey);

        // When
        List<ApiKeyEntity> testUserKeys = apiKeyRepository.findByOwnerIdAndRevokedFalse(testUser.getId());
        List<ApiKeyEntity> otherUserKeys = apiKeyRepository.findByOwnerIdAndRevokedFalse(otherUser.getId());

        // Then
        assertThat(testUserKeys).hasSize(1);
        assertThat(testUserKeys.get(0).getKeyHash()).isEqualTo("test-user-key");

        assertThat(otherUserKeys).hasSize(1);
        assertThat(otherUserKeys.get(0).getKeyHash()).isEqualTo("other-user-key");
    }

    @Test
    void shouldHandleExpiredButNotRevokedKeys() {
        // Given
        ApiKeyEntity expiredKey = ApiKeyEntity.builder()
                .keyHash("expired-key")
                .owner(testUser)
                .name("Test Key")
                .permissions(defaultPermissions())
                .expiresAt(Instant.now().minus(1, ChronoUnit.DAYS))
                .revoked(false)
                .build();
        entityManager.persistAndFlush(expiredKey);

        // When
        List<ApiKeyEntity> keys = apiKeyRepository.findByOwnerIdAndRevokedFalse(testUser.getId());

        // Then
        assertThat(keys).hasSize(1);
        assertThat(keys.get(0).getExpiresAt()).isBefore(Instant.now());
        assertThat(keys.get(0).isRevoked()).isFalse();
    }

    @Test
    void shouldFindKeyByIdSuccessfully() {
        // Given
        ApiKeyEntity apiKey = ApiKeyEntity.builder()
                .keyHash("find-by-id-key")
                .owner(testUser)
                .name("Test Key")
                .permissions(defaultPermissions())
                .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS))
                .revoked(false)
                .build();
        ApiKeyEntity savedKey = entityManager.persistAndFlush(apiKey);

        // When
        Optional<ApiKeyEntity> foundKey = apiKeyRepository.findById(savedKey.getId());

        // Then
        assertThat(foundKey).isPresent();
        assertThat(foundKey.get().getId()).isEqualTo(savedKey.getId());
        assertThat(foundKey.get().getKeyHash()).isEqualTo("find-by-id-key");
    }

    @Test
    void shouldCountApiKeysCorrectly() {
        // Given
        ApiKeyEntity key1 = ApiKeyEntity.builder()
                .keyHash("count-key-1")
                .owner(testUser)
                .name("Test Key")
                .permissions(defaultPermissions())
                .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS))
                .build();

        ApiKeyEntity key2 = ApiKeyEntity.builder()
                .keyHash("count-key-2")
                .owner(testUser)
                .name("Test Key")
                .permissions(defaultPermissions())
                .expiresAt(Instant.now().plus(2, ChronoUnit.DAYS))
                .build();

        entityManager.persistAndFlush(key1);
        entityManager.persistAndFlush(key2);

        // When
        long count = apiKeyRepository.count();

        // Then
        assertThat(count).isGreaterThanOrEqualTo(2);
    }

    @Test
    void shouldHandleMultipleKeysWithDifferentExpirationTimes() {
        // Given
        ApiKeyEntity shortLivedKey = ApiKeyEntity.builder()
                .keyHash("short-lived")
                .owner(testUser)
                .name("Test Key")
                .permissions(defaultPermissions())
                .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .revoked(false)
                .build();

        ApiKeyEntity longLivedKey = ApiKeyEntity.builder()
                .keyHash("long-lived")
                .owner(testUser)
                .name("Test Key")
                .permissions(defaultPermissions())
                .expiresAt(Instant.now().plus(365, ChronoUnit.DAYS))
                .revoked(false)
                .build();

        entityManager.persistAndFlush(shortLivedKey);
        entityManager.persistAndFlush(longLivedKey);

        // When
        List<ApiKeyEntity> keys = apiKeyRepository.findByOwnerIdAndRevokedFalse(testUser.getId());

        // Then
        assertThat(keys).hasSize(2);
        assertThat(keys)
                .extracting(ApiKeyEntity::getKeyHash)
                .containsExactlyInAnyOrder("short-lived", "long-lived");
    }
}

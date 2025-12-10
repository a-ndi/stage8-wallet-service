package com.stage8.wallet.repository;

import com.stage8.wallet.model.entity.UserEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void shouldFindUserByEmail() {
        // Given
        UserEntity user = UserEntity.builder()
                .email("test@example.com")
                .name("Test User")
                .googleId("google-123")
                .build();
        entityManager.persistAndFlush(user);

        // When
        Optional<UserEntity> foundUser = userRepository.findByEmail("test@example.com");

        // Then
        assertThat(foundUser).isPresent();
        assertThat(foundUser.get().getEmail()).isEqualTo("test@example.com");
        assertThat(foundUser.get().getName()).isEqualTo("Test User");
        assertThat(foundUser.get().getGoogleId()).isEqualTo("google-123");
    }

    @Test
    void shouldReturnEmptyWhenEmailNotFound() {
        // When
        Optional<UserEntity> foundUser = userRepository.findByEmail("nonexistent@example.com");

        // Then
        assertThat(foundUser).isEmpty();
    }

    @Test
    void shouldFindUserByGoogleId() {
        // Given
        UserEntity user = UserEntity.builder()
                .email("google@example.com")
                .name("Google User")
                .googleId("google-456")
                .build();
        entityManager.persistAndFlush(user);

        // When
        Optional<UserEntity> foundUser = userRepository.findByGoogleId("google-456");

        // Then
        assertThat(foundUser).isPresent();
        assertThat(foundUser.get().getGoogleId()).isEqualTo("google-456");
        assertThat(foundUser.get().getEmail()).isEqualTo("google@example.com");
        assertThat(foundUser.get().getName()).isEqualTo("Google User");
    }

    @Test
    void shouldReturnEmptyWhenGoogleIdNotFound() {
        // When
        Optional<UserEntity> foundUser = userRepository.findByGoogleId("nonexistent-google-id");

        // Then
        assertThat(foundUser).isEmpty();
    }

    @Test
    void shouldSaveUserSuccessfully() {
        // Given
        UserEntity user = UserEntity.builder()
                .email("save@example.com")
                .name("Save User")
                .googleId("google-789")
                .build();

        // When
        UserEntity savedUser = userRepository.save(user);
        entityManager.flush();

        // Then
        assertThat(savedUser).isNotNull();
        assertThat(savedUser.getId()).isNotNull();
        assertThat(savedUser.getEmail()).isEqualTo("save@example.com");
        assertThat(savedUser.getName()).isEqualTo("Save User");
        assertThat(savedUser.getGoogleId()).isEqualTo("google-789");
    }

    @Test
    void shouldUpdateExistingUser() {
        // Given
        UserEntity user = UserEntity.builder()
                .email("update@example.com")
                .name("Old Name")
                .googleId("google-old")
                .build();
        UserEntity savedUser = entityManager.persistAndFlush(user);

        // When
        savedUser.setName("New Name");
        savedUser.setGoogleId("google-new");
        UserEntity updatedUser = userRepository.save(savedUser);
        entityManager.flush();

        // Then
        assertThat(updatedUser.getId()).isEqualTo(savedUser.getId());
        assertThat(updatedUser.getName()).isEqualTo("New Name");
        assertThat(updatedUser.getGoogleId()).isEqualTo("google-new");
        assertThat(updatedUser.getEmail()).isEqualTo("update@example.com");
    }

    @Test
    void shouldDeleteUser() {
        // Given
        UserEntity user = UserEntity.builder()
                .email("delete@example.com")
                .name("Delete User")
                .googleId("google-delete")
                .build();
        UserEntity savedUser = entityManager.persistAndFlush(user);
        Long userId = savedUser.getId();

        // When
        userRepository.delete(savedUser);
        entityManager.flush();

        // Then
        Optional<UserEntity> foundUser = userRepository.findById(userId);
        assertThat(foundUser).isEmpty();
    }

    @Test
    void shouldFindUserById() {
        // Given
        UserEntity user = UserEntity.builder()
                .email("findbyid@example.com")
                .name("Find By Id User")
                .googleId("google-findbyid")
                .build();
        UserEntity savedUser = entityManager.persistAndFlush(user);

        // When
        Optional<UserEntity> foundUser = userRepository.findById(savedUser.getId());

        // Then
        assertThat(foundUser).isPresent();
        assertThat(foundUser.get().getId()).isEqualTo(savedUser.getId());
        assertThat(foundUser.get().getEmail()).isEqualTo("findbyid@example.com");
    }

    @Test
    void shouldHandleUserWithNullName() {
        // Given
        UserEntity user = UserEntity.builder()
                .email("noname@example.com")
                .name(null)
                .googleId("google-noname")
                .build();

        // When
        UserEntity savedUser = userRepository.save(user);
        entityManager.flush();

        // Then
        assertThat(savedUser).isNotNull();
        assertThat(savedUser.getName()).isNull();
        assertThat(savedUser.getEmail()).isEqualTo("noname@example.com");
    }

    @Test
    void shouldHandleUserWithNullGoogleId() {
        // Given
        UserEntity user = UserEntity.builder()
                .email("nogoogleid@example.com")
                .name("No Google ID User")
                .googleId(null)
                .build();

        // When
        UserEntity savedUser = userRepository.save(user);
        entityManager.flush();

        // Then
        assertThat(savedUser).isNotNull();
        assertThat(savedUser.getGoogleId()).isNull();
        assertThat(savedUser.getEmail()).isEqualTo("nogoogleid@example.com");
    }

    @Test
    void shouldFindMultipleUsers() {
        // Given
        UserEntity user1 = UserEntity.builder()
                .email("user1@example.com")
                .name("User 1")
                .googleId("google-1")
                .build();
        UserEntity user2 = UserEntity.builder()
                .email("user2@example.com")
                .name("User 2")
                .googleId("google-2")
                .build();
        entityManager.persistAndFlush(user1);
        entityManager.persistAndFlush(user2);

        // When
        long count = userRepository.count();

        // Then
        assertThat(count).isGreaterThanOrEqualTo(2);
    }

    @Test
    void shouldReturnTrueIfUserExistsById() {
        // Given
        UserEntity user = UserEntity.builder()
                .email("exists@example.com")
                .name("Exists User")
                .googleId("google-exists")
                .build();
        UserEntity savedUser = entityManager.persistAndFlush(user);

        // When
        boolean exists = userRepository.existsById(savedUser.getId());

        // Then
        assertThat(exists).isTrue();
    }

    @Test
    void shouldReturnFalseIfUserDoesNotExistById() {
        // When
        boolean exists = userRepository.existsById(99999L);

        // Then
        assertThat(exists).isFalse();
    }
}

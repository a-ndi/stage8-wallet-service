package com.stage8.wallet.repository;

import com.stage8.wallet.model.entity.ApiKeyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ApiKeyRepository extends JpaRepository<ApiKeyEntity, Long> {

    List<ApiKeyEntity> findByUserIdAndRevokedFalse(Long userId);
    Optional<ApiKeyEntity> findByHashedKey(String hashedKey);
}

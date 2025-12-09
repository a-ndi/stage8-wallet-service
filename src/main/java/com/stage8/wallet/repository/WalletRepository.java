package com.stage8.wallet.repository;

import com.stage8.wallet.model.entity.WalletEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WalletRepository extends JpaRepository<WalletEntity, Long> {

    Optional<WalletEntity> findByUser_Id(Long userId);
    Optional<WalletEntity> findByWalletNumber(String walletNumber);
}

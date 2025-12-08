package com.stage8.wallet.repository;

import com.stage8.wallet.model.entity.WalletEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalletRepository extends JpaRepository<WalletEntity, Long> {

    WalletEntity findByUserId(Long userId);
    WalletEntity findByWalletNumber(String walletNumber);
}

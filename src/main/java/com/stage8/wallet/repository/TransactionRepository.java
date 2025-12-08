package com.stage8.wallet.repository;

import com.stage8.wallet.model.entity.TransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<TransactionEntity , Long> {

    Optional<TransactionEntity> findByReference(String reference);
    List<TransactionEntity> findByUserId(Long userId);
}

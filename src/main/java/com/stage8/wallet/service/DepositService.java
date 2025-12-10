package com.stage8.wallet.service;

import com.stage8.wallet.model.entity.TransactionEntity;
import com.stage8.wallet.model.entity.UserEntity;
import com.stage8.wallet.model.enums.TransactionStatus;
import com.stage8.wallet.model.enums.TransactionType;
import com.stage8.wallet.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class DepositService {

    private final TransactionRepository transactionRepository;
    private final PaystackService paystackService;

    /**
     * Creates a pending deposit transaction and initializes Paystack payment
     * 
     * CRITICAL: Every change must create a transaction record
     * This creates a PENDING transaction that will be updated by the webhook
     * 
     * @param user The user making the deposit
     * @param amountInKobo Amount in kobo (integer)
     */
    @Transactional
    public DepositResult initializeDeposit(UserEntity user, Long amountInKobo) {
        // Generate unique transaction reference (ensures uniqueness for idempotency)
        String reference = generateTransactionReference();

        // CRITICAL: Every change must create a transaction record
        // Create pending transaction record (will be updated by webhook)
        TransactionEntity transaction = TransactionEntity.builder()
                .reference(reference)
                .user(user)
                .type(TransactionType.DEPOSIT)
                .status(TransactionStatus.PENDING)
                .amount(amountInKobo)
                .createdAt(LocalDateTime.now())
                .build();

        transaction = transactionRepository.save(transaction);

        // Initialize Paystack transaction
        PaystackService.PaystackInitResponse paystackResponse = paystackService.initializeTransaction(
                amountInKobo,
                user.getEmail(),
                reference
        );

        return new DepositResult(transaction, paystackResponse.getReference(), paystackResponse.getAuthorizationUrl());
    }

    /**
     * Generates a unique transaction reference
     */
    private String generateTransactionReference() {
        SecureRandom random = new SecureRandom();
        String reference;
        do {
            // Generate 16-character alphanumeric reference
            StringBuilder sb = new StringBuilder();
            String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
            for (int i = 0; i < 16; i++) {
                sb.append(chars.charAt(random.nextInt(chars.length())));
            }
            reference = sb.toString();
        } while (transactionRepository.findByReference(reference).isPresent());

        return reference;
    }

    /**
     * Result class for deposit initialization
     */
    public static class DepositResult {
        private final TransactionEntity transaction;
        private final String reference;
        private final String authorizationUrl;

        public DepositResult(TransactionEntity transaction, String reference, String authorizationUrl) {
            this.transaction = transaction;
            this.reference = reference;
            this.authorizationUrl = authorizationUrl;
        }

        public TransactionEntity getTransaction() {
            return transaction;
        }

        public String getReference() {
            return reference;
        }

        public String getAuthorizationUrl() {
            return authorizationUrl;
        }
    }
}


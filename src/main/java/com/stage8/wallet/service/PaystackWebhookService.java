package com.stage8.wallet.service;

import com.stage8.wallet.model.entity.TransactionEntity;
import com.stage8.wallet.model.entity.WalletEntity;
import com.stage8.wallet.model.enums.TransactionStatus;
import com.stage8.wallet.repository.TransactionRepository;
import com.stage8.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaystackWebhookService {

    @Value("${paystack.webhook-secret}")
    private String webhookSecret;

    private final TransactionRepository transactionRepository;
    private final WalletRepository walletRepository;

    /**
     * Validates Paystack webhook signature using HMAC SHA512
     */
    public boolean validateSignature(String payload, String signature) {
        if (signature == null || payload == null) {
            return false;
        }

        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            SecretKeySpec secretKeySpec = new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA512");
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            
            // Convert to hex string
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            
            // Paystack sends signature in hex format
            return hexString.toString().equals(signature);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            return false;
        }
    }

    /**
     * Processes Paystack webhook event
     * Updates transaction status and wallet balance (idempotent)
     * 
     * IDEMPOTENCY PROTECTION:
     * - Checks if reference has already been processed with final status (SUCCESS or FAILED)
     * - Prevents double-crediting from:
     *   - Paystack sending the same webhook twice
     *   - Network retry issues
     *   - User clicking "Pay Now" multiple times
     *   - Background jobs running again
     * 
     * @param reference Transaction reference
     * @param status Payment status from Paystack
     * @param amountInKobo Amount in kobo from Paystack webhook (used to update transaction amount if needed)
     */
    @Transactional
    public void processWebhookEvent(String reference, String status, Long amountInKobo) {
        log.info("Processing webhook event - Reference: {}, Status: {}, Amount (kobo): {}", reference, status, amountInKobo);

        // Find transaction by reference
        TransactionEntity transaction = transactionRepository.findByReference(reference)
                .orElseThrow(() -> {
                    log.error("Webhook processing failed - Transaction not found: {}", reference);
                    return new RuntimeException("Transaction not found: " + reference);
                });

        // Update transaction amount from webhook if provided (ensures accuracy)
        if (amountInKobo != null && amountInKobo > 0) {
            transaction.setAmount(amountInKobo);
            log.info("Transaction amount updated from webhook - Reference: {}, Amount (kobo): {}", reference, amountInKobo);
        }

        // IDEMPOTENCY CHECK: Has this reference already been processed?
        // Check if transaction already has a final status (SUCCESS or FAILED)
        // This prevents double-processing from any source (webhook retries, network issues, etc.)
        if (transaction.getStatus() == TransactionStatus.SUCCESS) {
            log.warn("Webhook idempotency check - Reference {} already processed as SUCCESS. Skipping to prevent double-credit.", reference);
            return; // Already processed successfully - do NOT credit again
        }
        
        if (transaction.getStatus() == TransactionStatus.FAILED) {
            log.warn("Webhook idempotency check - Reference {} already processed as FAILED. Skipping duplicate webhook.", reference);
            return; // Already processed as failed - do NOT process again
        }

        // Determine new status from webhook
        TransactionStatus newStatus = "success".equalsIgnoreCase(status) 
                ? TransactionStatus.SUCCESS 
                : TransactionStatus.FAILED;
        
        log.info("Updating transaction status - Reference: {}, Old Status: {}, New Status: {}", 
                reference, transaction.getStatus(), newStatus);

        // Update transaction status
        transaction.setStatus(newStatus);
        transactionRepository.save(transaction);

        // CRITICAL: Only add balance if payment is successful
        // This ensures we never credit wallets for failed payments
        if (newStatus == TransactionStatus.SUCCESS) {
            log.info("Payment successful - Crediting wallet for reference: {}", reference);
            updateWalletBalance(transaction);
        } else {
            log.info("Payment failed - NOT crediting wallet for reference: {}", reference);
        }
    }

    /**
     * Updates wallet balance for successful deposit transaction
     * CRITICAL: Only called when payment status is SUCCESS
     * This ensures we only add balance if payment is successful
     */
    private void updateWalletBalance(TransactionEntity transaction) {
        if (transaction.getUser() == null) {
            log.error("Cannot credit wallet - Transaction has no user: {}", transaction.getReference());
            throw new RuntimeException("Transaction has no user");
        }

        WalletEntity wallet = walletRepository.findByUser_Id(transaction.getUser().getId())
                .orElseThrow(() -> {
                    log.error("Cannot credit wallet - Wallet not found for user ID: {}", transaction.getUser().getId());
                    return new RuntimeException("Wallet not found for user");
                });

        // Record balance before credit for logging
        Long balanceBefore = wallet.getBalance();
        
        // Add deposit amount to wallet balance (ONLY for successful payments)
        wallet.setBalance(wallet.getBalance() + transaction.getAmount());
        walletRepository.save(wallet);
        
        log.info("Wallet balance credited - Reference: {}, Wallet: {}, Amount: {}, Balance Before: {}, Balance After: {}", 
                transaction.getReference(), wallet.getWalletNumber(), transaction.getAmount(), balanceBefore, wallet.getBalance());
    }
}


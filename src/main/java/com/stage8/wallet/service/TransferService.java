package com.stage8.wallet.service;

import com.stage8.wallet.model.entity.TransactionEntity;
import com.stage8.wallet.model.entity.UserEntity;
import com.stage8.wallet.model.entity.WalletEntity;
import com.stage8.wallet.model.enums.TransactionStatus;
import com.stage8.wallet.model.enums.TransactionType;
import com.stage8.wallet.repository.TransactionRepository;
import com.stage8.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransferService {

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;

    /**
     * Processes a wallet-to-wallet transfer
     * Validates balance, performs atomic transaction, and logs all operations
     * 
     * @param sender The user initiating the transfer
     * @param recipientWalletNumber The wallet number of the recipient
     * @param amountInKobo The amount to transfer in kobo (must be positive)
     * @throws IllegalArgumentException if validation fails (insufficient balance, invalid wallet, self-transfer)
     * @throws RuntimeException if database operation fails
     */
    @Transactional(rollbackFor = Exception.class)
    public void transfer(UserEntity sender, String recipientWalletNumber, Long amountInKobo) {
        log.info("Transfer initiated - Sender ID: {}, Recipient Wallet: {}, Amount (kobo): {}", 
                sender.getId(), recipientWalletNumber, amountInKobo);

        // Validate amount is positive
        if (amountInKobo == null || amountInKobo <= 0) {
            log.error("Transfer failed - Invalid amount: {} for sender ID: {}", amountInKobo, sender.getId());
            throw new IllegalArgumentException("Transfer amount must be greater than zero");
        }

        // Get sender's wallet
        WalletEntity senderWallet = walletRepository.findByUser_Id(sender.getId())
                .orElseThrow(() -> {
                    log.error("Transfer failed - Sender wallet not found for user ID: {}", sender.getId());
                    return new RuntimeException("Sender wallet not found");
                });

        log.debug("Sender wallet retrieved - Wallet ID: {}, Wallet Number: {}, Current Balance: {}", 
                senderWallet.getId(), senderWallet.getWalletNumber(), senderWallet.getBalance());

        // CRITICAL: Only reduce balance if there is enough money
        // This prevents negative balances and ensures transfers only happen when sufficient funds exist
        if (senderWallet.getBalance() < amountInKobo) {
            log.error("Transfer failed - Insufficient balance - Sender ID: {}, Wallet: {}, Balance: {}, Requested: {}", 
                    sender.getId(), senderWallet.getWalletNumber(), senderWallet.getBalance(), amountInKobo);
            throw new IllegalArgumentException("Insufficient balance");
        }

        // Get recipient's wallet
        WalletEntity recipientWallet = walletRepository.findByWalletNumber(recipientWalletNumber)
                .orElseThrow(() -> {
                    log.error("Transfer failed - Recipient wallet not found: {}", recipientWalletNumber);
                    return new IllegalArgumentException("Recipient wallet not found");
                });

        log.debug("Recipient wallet retrieved - Wallet ID: {}, Wallet Number: {}, Current Balance: {}", 
                recipientWallet.getId(), recipientWallet.getWalletNumber(), recipientWallet.getBalance());

        // Prevent self-transfer
        if (senderWallet.getId().equals(recipientWallet.getId())) {
            log.error("Transfer failed - Self-transfer attempt - Wallet: {}", senderWallet.getWalletNumber());
            throw new IllegalArgumentException("Cannot transfer to own wallet");
        }

        // Generate transaction reference
        String reference = generateTransactionReference();
        log.debug("Transaction reference generated: {}", reference);

        // Get recipient user
        UserEntity recipient = recipientWallet.getUser();
        if (recipient == null) {
            log.error("Transfer failed - Recipient wallet has no owner - Wallet: {}", recipientWalletNumber);
            throw new RuntimeException("Recipient wallet has no owner");
        }

        // Record balances before transfer for logging
        Long senderBalanceBefore = senderWallet.getBalance();
        Long recipientBalanceBefore = recipientWallet.getBalance();

        // Deduct from sender
        senderWallet.setBalance(senderWallet.getBalance() - amountInKobo);
        walletRepository.save(senderWallet);
        log.info("Amount deducted from sender - Wallet: {}, Amount: {}, Balance Before: {}, Balance After: {}", 
                senderWallet.getWalletNumber(), amountInKobo, senderBalanceBefore, senderWallet.getBalance());

        // Add to recipient
        recipientWallet.setBalance(recipientWallet.getBalance() + amountInKobo);
        walletRepository.save(recipientWallet);
        log.info("Amount credited to recipient - Wallet: {}, Amount: {}, Balance Before: {}, Balance After: {}", 
                recipientWallet.getWalletNumber(), amountInKobo, recipientBalanceBefore, recipientWallet.getBalance());

        // Create transaction for sender (OUTGOING)
        TransactionEntity senderTransaction = TransactionEntity.builder()
                .reference(reference + "_OUT")
                .user(sender)
                .type(TransactionType.TRANSFER)
                .status(TransactionStatus.SUCCESS)
                .amount(-amountInKobo) // Negative for outgoing
                .createdAt(LocalDateTime.now())
                .build();
        TransactionEntity savedSenderTransaction = transactionRepository.save(senderTransaction);
        log.info("Sender transaction recorded - Reference: {}, Transaction ID: {}, Amount: {}", 
                savedSenderTransaction.getReference(), savedSenderTransaction.getId(), savedSenderTransaction.getAmount());

        // Create transaction for recipient (INCOMING)
        TransactionEntity recipientTransaction = TransactionEntity.builder()
                .reference(reference + "_IN")
                .user(recipient)
                .type(TransactionType.TRANSFER)
                .status(TransactionStatus.SUCCESS)
                .amount(amountInKobo) // Positive for incoming
                .createdAt(LocalDateTime.now())
                .build();
        TransactionEntity savedRecipientTransaction = transactionRepository.save(recipientTransaction);
        log.info("Recipient transaction recorded - Reference: {}, Transaction ID: {}, Amount: {}", 
                savedRecipientTransaction.getReference(), savedRecipientTransaction.getId(), savedRecipientTransaction.getAmount());

        log.info("Transfer completed successfully - Reference: {}, Sender: {}, Recipient: {}, Amount: {}", 
                reference, senderWallet.getWalletNumber(), recipientWallet.getWalletNumber(), amountInKobo);
    }

    /**
     * Generates a unique transaction reference
     * Ensures uniqueness by checking against existing transaction references
     */
    private String generateTransactionReference() {
        SecureRandom random = new SecureRandom();
        String reference;
        int attempts = 0;
        int maxAttempts = 10; // Prevent infinite loop
        
        do {
            // Generate 16-character alphanumeric reference
            StringBuilder sb = new StringBuilder();
            String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
            for (int i = 0; i < 16; i++) {
                sb.append(chars.charAt(random.nextInt(chars.length())));
            }
            reference = sb.toString();
            attempts++;
            
            if (attempts >= maxAttempts) {
                log.error("Failed to generate unique transaction reference after {} attempts", maxAttempts);
                throw new RuntimeException("Unable to generate unique transaction reference");
            }
        } while (transactionRepository.findByReference(reference + "_OUT").isPresent() ||
                 transactionRepository.findByReference(reference + "_IN").isPresent());

        log.debug("Generated unique transaction reference: {} (attempts: {})", reference, attempts);
        return reference;
    }
}


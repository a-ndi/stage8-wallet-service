package com.stage8.wallet.controller;

import com.stage8.wallet.dto.DepositRequest;
import com.stage8.wallet.dto.DepositResponse;
import com.stage8.wallet.dto.DepositStatusResponse;
import com.stage8.wallet.dto.TransactionHistoryResponse;
import com.stage8.wallet.dto.TransferRequest;
import com.stage8.wallet.dto.TransferResponse;
import com.stage8.wallet.model.entity.TransactionEntity;
import com.stage8.wallet.model.entity.UserEntity;
import com.stage8.wallet.model.entity.WalletEntity;
import com.stage8.wallet.model.enums.Permission;
import com.stage8.wallet.model.enums.TransactionType;
import com.stage8.wallet.repository.TransactionRepository;
import com.stage8.wallet.repository.UserRepository;
import com.stage8.wallet.repository.WalletRepository;
import com.stage8.wallet.service.DepositService;
import com.stage8.wallet.service.TransferService;
import com.stage8.wallet.utility.PermissionChecker;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/wallet")
@RequiredArgsConstructor
@Tag(name = "Wallet Operations", description = "Wallet deposit, transfer, balance, and transaction history endpoints")
public class WalletController {

    private final DepositService depositService;
    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final TransferService transferService;
    private final TransactionRepository transactionRepository;

    @Operation(
            summary = "Initialize Deposit",
            description = "Initializes a wallet deposit transaction via Paystack. Creates a pending transaction and returns Paystack authorization URL for payment.",
            security = {@SecurityRequirement(name = "Bearer Authentication"), @SecurityRequirement(name = "API Key Authentication")}
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Deposit initialized successfully",
                    content = @Content(schema = @Schema(implementation = DepositResponse.class))
            ),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden - DEPOSIT permission required")
    })
    @PostMapping("/deposit")
    public ResponseEntity<?> deposit(@Valid @RequestBody DepositRequest request) {
        try {
            // Get authentication
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || authentication.getName() == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            // Check permission (JWT allows all, API key needs DEPOSIT permission)
            if (!PermissionChecker.hasPermission(authentication, Permission.DEPOSIT)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Insufficient permissions. DEPOSIT permission required."));
            }

            // Get user
            Long userId = Long.parseLong(authentication.getName());
            UserEntity user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            // Initialize deposit
            DepositService.DepositResult result = depositService.initializeDeposit(
                    user,
                    request.getAmount()
            );

            // Build response
            DepositResponse response = DepositResponse.builder()
                    .reference(result.getReference())
                    .authorizationUrl(result.getAuthorizationUrl())
                    .build();

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to initialize deposit: " + e.getMessage()));
        }
    }

    @Operation(
            summary = "Get Wallet Balance",
            description = "Retrieves the current balance for the authenticated user's wallet.",
            security = {@SecurityRequirement(name = "Bearer Authentication"), @SecurityRequirement(name = "API Key Authentication")}
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Balance retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden - READ permission required")
    })
    @GetMapping("/balance")
    public ResponseEntity<?> getBalance() {
        try {
            // Get authentication
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || authentication.getName() == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            // Check permission (JWT allows all, API key needs READ permission)
            if (!PermissionChecker.hasPermission(authentication, Permission.READ)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Insufficient permissions. READ permission required."));
            }

            // Get user
            Long userId = Long.parseLong(authentication.getName());
            UserEntity user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            // Get wallet
            WalletEntity wallet = walletRepository.findByUser_Id(user.getId())
                    .orElseThrow(() -> new RuntimeException("Wallet not found"));

            // Build response with just balance
            return ResponseEntity.ok(Map.of("balance", wallet.getBalance()));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to get balance: " + e.getMessage()));
        }
    }

    @Operation(
            summary = "Wallet-to-Wallet Transfer",
            description = "Transfers funds from the authenticated user's wallet to another wallet. " +
                    "Validates balance, prevents self-transfer, and creates transaction records for both parties. " +
                    "Transaction is atomic (all-or-nothing).",
            security = {@SecurityRequirement(name = "Bearer Authentication"), @SecurityRequirement(name = "API Key Authentication")}
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Transfer completed successfully",
                    content = @Content(schema = @Schema(implementation = TransferResponse.class))
            ),
            @ApiResponse(responseCode = "400", description = "Invalid request (insufficient balance, recipient not found, self-transfer)"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden - TRANSFER permission required")
    })
    @PostMapping("/transfer")
    public ResponseEntity<?> transfer(@Valid @RequestBody TransferRequest request) {
        try {
            // Get authentication
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || authentication.getName() == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            // Check permission (JWT allows all, API key needs TRANSFER permission)
            if (!PermissionChecker.hasPermission(authentication, Permission.TRANSFER)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Insufficient permissions. TRANSFER permission required."));
            }

            // Get user
            Long userId = Long.parseLong(authentication.getName());
            UserEntity user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            // Process transfer
            transferService.transfer(user, request.getWalletNumber(), request.getAmount());

            // Build response
            TransferResponse response = TransferResponse.builder()
                    .status("success")
                    .message("Transfer completed")
                    .build();

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            // Handle specific validation errors with clear messages
            String errorMessage = e.getMessage();
            if (errorMessage != null && errorMessage.contains("Insufficient balance")) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Insufficient balance"));
            } else if (errorMessage != null && errorMessage.contains("Recipient wallet not found")) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Recipient wallet not found"));
            } else if (errorMessage != null && errorMessage.contains("Cannot transfer to own wallet")) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Cannot transfer to own wallet"));
            }
            return ResponseEntity.badRequest()
                    .body(Map.of("error", errorMessage != null ? errorMessage : "Invalid transfer request"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to process transfer: " + e.getMessage()));
        }
    }

    @Operation(
            summary = "Get Transaction History",
            description = "Retrieves all transactions for the authenticated user. Returns deposit and transfer transactions with status.",
            security = {@SecurityRequirement(name = "Bearer Authentication"), @SecurityRequirement(name = "API Key Authentication")}
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Transaction history retrieved successfully",
                    content = @Content(schema = @Schema(implementation = TransactionHistoryResponse.class))
            ),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden - READ permission required")
    })
    @GetMapping("/transactions")
    public ResponseEntity<?> getTransactions() {
        try {
            // Get authentication
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || authentication.getName() == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            // Check permission (JWT allows all, API key needs READ permission)
            if (!PermissionChecker.hasPermission(authentication, Permission.READ)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Insufficient permissions. READ permission required."));
            }

            // Get user
            Long userId = Long.parseLong(authentication.getName());
            UserEntity user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            // Get all transactions for the user
            List<TransactionEntity> transactions = transactionRepository.findByUser_Id(user.getId());

            // Map to response DTOs
            List<TransactionHistoryResponse> response = transactions.stream()
                    .map(transaction -> TransactionHistoryResponse.builder()
                            .type(transaction.getType() != null 
                                    ? transaction.getType().name().toLowerCase() 
                                    : null)
                            .amount(transaction.getAmount() != null 
                                    ? Math.abs(transaction.getAmount()) // Return absolute value
                                    : null)
                            .status(transaction.getStatus() != null 
                                    ? transaction.getStatus().name().toLowerCase() 
                                    : null)
                            .build())
                    .collect(Collectors.toList());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to get transactions: " + e.getMessage()));
        }
    }

    @Operation(
            summary = "Get Deposit Status",
            description = "Retrieves the status of a deposit transaction by reference. " +
                    "⚠️ WARNING: This is a read-only endpoint and does NOT credit wallets. " +
                    "Only the Paystack webhook is allowed to credit wallets.",
            security = {@SecurityRequirement(name = "Bearer Authentication"), @SecurityRequirement(name = "API Key Authentication")}
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Deposit status retrieved successfully",
                    content = @Content(schema = @Schema(implementation = DepositStatusResponse.class))
            ),
            @ApiResponse(responseCode = "400", description = "Invalid request (not a deposit transaction)"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden - READ permission required or transaction does not belong to user"),
            @ApiResponse(responseCode = "404", description = "Transaction not found")
    })
    @GetMapping("/deposit/{reference}/status")
    public ResponseEntity<?> getDepositStatus(
            @Parameter(description = "Transaction reference", required = true)
            @PathVariable String reference) {
        try {
            // Get authentication
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || authentication.getName() == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            // Check permission (JWT allows all, API key needs READ permission)
            if (!PermissionChecker.hasPermission(authentication, Permission.READ)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Insufficient permissions. READ permission required."));
            }

            // Find transaction by reference
            TransactionEntity transaction = transactionRepository.findByReference(reference)
                    .orElseThrow(() -> new RuntimeException("Transaction not found"));

            // Verify it's a deposit transaction
            if (transaction.getType() != TransactionType.DEPOSIT) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Transaction is not a deposit"));
            }

            // Optional: Verify transaction belongs to authenticated user (security check)
            Long userId = Long.parseLong(authentication.getName());
            if (transaction.getUser() == null || !transaction.getUser().getId().equals(userId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Transaction does not belong to authenticated user"));
            }

            // Build response (READ-ONLY - no wallet operations)
            DepositStatusResponse response = DepositStatusResponse.builder()
                    .reference(transaction.getReference())
                    .status(transaction.getStatus() != null 
                            ? transaction.getStatus().name().toLowerCase() 
                            : null)
                    .amount(transaction.getAmount() != null 
                            ? Math.abs(transaction.getAmount()) // Return absolute value
                            : null)
                    .build();

            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("not found")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", e.getMessage()));
            }
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to get deposit status: " + e.getMessage()));
        }
    }
}


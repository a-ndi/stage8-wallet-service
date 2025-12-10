package com.stage8.wallet.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransferRequest {
    
    @NotBlank(message = "Wallet number is required")
    @JsonProperty("wallet_number")
    private String walletNumber;
    
    /**
     * Amount in kobo (smallest Nigerian currency unit) as integer
     * Example: 5000 kobo = 50 Naira
     * Minimum: 100 kobo (1 Naira)
     */
    @NotNull(message = "Amount is required")
    @Min(value = 100, message = "Amount must be at least 100 kobo (1 Naira)")
    private Long amount;
}


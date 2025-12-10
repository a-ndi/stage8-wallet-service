package com.stage8.wallet.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DepositStatusResponse {
    private String reference;
    private String status;
    /**
     * Amount in kobo (integer)
     */
    private Long amount;
}


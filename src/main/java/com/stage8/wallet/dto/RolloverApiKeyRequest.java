package com.stage8.wallet.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RolloverApiKeyRequest {
    
    @NotBlank(message = "Expired key ID is required")
    @JsonProperty("expired_key_id")
    private String expiredKeyId;
    
    @NotBlank(message = "Expiry is required")
    private String expiry;
}


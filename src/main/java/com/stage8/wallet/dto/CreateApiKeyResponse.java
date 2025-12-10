package com.stage8.wallet.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateApiKeyResponse {
    @JsonProperty("api_key")
    private String apiKey; // Only returned once - plain text key
    
    @JsonProperty("expires_at")
    private Instant expiresAt;
}


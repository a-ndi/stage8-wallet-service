package com.stage8.wallet.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DepositResponse {
    
    @JsonProperty("reference")
    private String reference;
    
    @JsonProperty("authorization_url")
    private String authorizationUrl;
}


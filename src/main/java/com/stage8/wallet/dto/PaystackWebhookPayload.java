package com.stage8.wallet.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class PaystackWebhookPayload {
    private String event;
    private PaystackWebhookData data;

    @Data
    public static class PaystackWebhookData {
        private String reference;
        private String status;
        private String gateway_response;
        
        @JsonProperty("amount")
        private Long amount; // Amount in kobo
    }
}


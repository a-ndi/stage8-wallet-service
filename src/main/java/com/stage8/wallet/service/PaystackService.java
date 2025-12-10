package com.stage8.wallet.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
public class PaystackService {

    @Value("${paystack.secret-key}")
    private String secretKey;

    @Value("${paystack.base-url}")
    private String baseUrl;

    private final RestTemplate restTemplate;

    public PaystackService() {
        this.restTemplate = new RestTemplate();
    }

    /**
     * Initializes a Paystack transaction
     * @param amountInKobo Amount in kobo (integer)
     * @param email User email
     * @param reference Transaction reference
     * @return PaystackInitResponse with authorization URL
     */
    public PaystackInitResponse initializeTransaction(Long amountInKobo, String email, String reference) {
        String url = baseUrl + "/transaction/initialize";

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + secretKey);
        headers.set("Content-Type", "application/json");

        // Amount is already in kobo as Long

        Map<String, Object> requestBody = Map.of(
                "amount", amountInKobo.longValue(),
                "email", email,
                "reference", reference
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<PaystackResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    request,
                    PaystackResponse.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                PaystackResponse paystackResponse = response.getBody();
                if (paystackResponse.isStatus() && paystackResponse.getData() != null) {
                    return new PaystackInitResponse(
                            paystackResponse.getData().getReference(),
                            paystackResponse.getData().getAuthorizationUrl()
                    );
                } else {
                    throw new RuntimeException("Paystack initialization failed: " + 
                            (paystackResponse.getMessage() != null ? paystackResponse.getMessage() : "Unknown error"));
                }
            } else {
                throw new RuntimeException("Paystack API returned error status: " + response.getStatusCode());
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize Paystack transaction: " + e.getMessage(), e);
        }
    }

    @Data
    private static class PaystackResponse {
        private boolean status;
        private String message;
        private PaystackData data;
    }

    @Data
    private static class PaystackData {
        private String reference;
        
        @JsonProperty("authorization_url")
        private String authorizationUrl;
    }

    @Data
    public static class PaystackInitResponse {
        private final String reference;
        
        @JsonProperty("authorization_url")
        private final String authorizationUrl;

        public PaystackInitResponse(String reference, String authorizationUrl) {
            this.reference = reference;
            this.authorizationUrl = authorizationUrl;
        }
    }
}


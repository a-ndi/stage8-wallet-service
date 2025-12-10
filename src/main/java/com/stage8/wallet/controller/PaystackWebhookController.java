package com.stage8.wallet.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stage8.wallet.dto.PaystackWebhookPayload;
import com.stage8.wallet.service.PaystackWebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/wallet/paystack")
@RequiredArgsConstructor
@Tag(name = "Paystack Webhook", description = "Paystack webhook receiver for transaction updates")
public class PaystackWebhookController {

    private final PaystackWebhookService paystackWebhookService;

    @Operation(
            summary = "Paystack Webhook Receiver",
            description = "Receives transaction updates from Paystack. Validates HMAC SHA512 signature, " +
                    "updates transaction status, and credits wallet balance on successful payment. " +
                    "Implements idempotency to prevent double-crediting. " +
                    " This endpoint is public (no authentication required) but validates Paystack signature.",
            hidden = false
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Webhook processed successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid Paystack signature")
    })
    @PostMapping("/webhook")
    public ResponseEntity<Map<String, Boolean>> paystackWebhook(
            HttpServletRequest request) {
        try {
            // Read request body as string for signature validation
            String payload = StreamUtils.copyToString(
                    request.getInputStream(), 
                    StandardCharsets.UTF_8
            );

            // Get Paystack signature from header
            String signature = request.getHeader("x-paystack-signature");
            if (signature == null || signature.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("status", false));
            }

            // Validate signature
            if (!paystackWebhookService.validateSignature(payload, signature)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("status", false));
            }

            // Parse webhook payload
            ObjectMapper objectMapper = new ObjectMapper();
            PaystackWebhookPayload webhookPayload = objectMapper.readValue(
                    payload, 
                    PaystackWebhookPayload.class
            );

            // Only process charge.success and charge.failed events
            if (webhookPayload.getData() == null || webhookPayload.getData().getReference() == null) {
                return ResponseEntity.ok(Map.of("status", true));
            }

            String event = webhookPayload.getEvent();
            String reference = webhookPayload.getData().getReference();
            String status = webhookPayload.getData().getStatus();
            Long amountInKobo = webhookPayload.getData().getAmount(); // Amount from Paystack is in kobo

            // Process only charge events
            if (event != null && (event.equals("charge.success") || event.equals("charge.failed"))) {
                // Process webhook event (updates transaction and wallet balance)
                // Pass amount in kobo to ensure correct amount is stored
                paystackWebhookService.processWebhookEvent(reference, status, amountInKobo);
            }

            return ResponseEntity.ok(Map.of("status", true));

        } catch (Exception e) {
            // Log error but return success to Paystack (to prevent retries for invalid requests)
            return ResponseEntity.ok(Map.of("status", true));
        }
    }
}


package com.stage8.wallet.dto;

import com.stage8.wallet.model.enums.Permission;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateApiKeyRequest {
    
    @NotBlank(message = "Name is required")
    private String name;
    
    @NotBlank(message = "Expiry is required")
    @Pattern(regexp = "^\\d+[HDMYhdmy]$", message = "Expiry must be in format: {number}H, {number}D, {number}M, or {number}Y (e.g., 2H, 7D, 6M, 1Y)")
    private String expiry;
    
    @NotEmpty(message = "At least one permission is required")
    private Set<Permission> permissions;
}


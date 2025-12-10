package com.stage8.wallet.dto;

import com.stage8.wallet.model.enums.Permission;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiKeyResponse {
    private Long id;
    private String name;
    private Set<Permission> permissions;
    private Instant expiresAt;
    private Instant createdAt;
    private boolean expired;
    private boolean revoked;
}

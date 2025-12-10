package com.stage8.wallet.utility;

import com.stage8.wallet.model.entity.ApiKeyEntity;
import com.stage8.wallet.model.enums.Permission;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

public class PermissionChecker {

    /**
     * Checks if the authenticated user has the required permission
     * Works for both JWT (always allowed) and API key (checks permissions)
     */
    public static boolean hasPermission(Authentication authentication, Permission requiredPermission) {
        if (authentication == null) {
            return false;
        }

        // If authenticated via API key, check permissions
        if (authentication.getPrincipal() instanceof ApiKeyEntity) {
            ApiKeyEntity apiKey = (ApiKeyEntity) authentication.getPrincipal();
            return apiKey.getPermissions() != null && 
                   apiKey.getPermissions().contains(requiredPermission);
        }

        // If authenticated via JWT (principal is String userId), allow (JWT users have all permissions)
        if (authentication.getPrincipal() instanceof String) {
            return true; // JWT authentication - allow all
        }

        // Check authorities (alternative method)
        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        if (authorities != null) {
            String requiredAuthority = "ROLE_" + requiredPermission.name();
            return authorities.stream()
                    .anyMatch(auth -> auth.getAuthority().equals(requiredAuthority));
        }

        return false;
    }
}


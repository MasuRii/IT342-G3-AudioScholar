package edu.cit.audioscholar.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateUserRoleRequest(
        @NotBlank(message = "Role cannot be blank") @Pattern(regexp = "^(ROLE_USER|ROLE_PREMIUM)$",
                message = "Role must be either 'ROLE_USER' or 'ROLE_PREMIUM'") String role) {
    public UpdateUserRoleRequest {
        if (role != null) {
            role = role.trim();
        }
    }
}

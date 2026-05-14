package org.example.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class AuthRequest {

    public record LoginRequest(
            @NotBlank String username,
            @NotBlank String password
    ) {}

    public record RegisterRequest(
            @NotBlank @Size(min = 3, max = 50) String username,
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8) String password,
            String firstName,
            String lastName
    ) {}

    public record RefreshTokenRequest(@NotBlank String refreshToken) {}

    public record ChangePasswordRequest(
            @NotBlank String oldPassword,
            @NotBlank @Size(min = 8) String newPassword
    ) {}

    public record ResetPasswordRequest(@NotBlank @Email String email) {}
}

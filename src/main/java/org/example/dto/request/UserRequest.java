package org.example.dto.request;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

public class UserRequest {

    public record UpdateProfileRequest(
            String firstName,
            String lastName,
            String phone,
            String address,
            LocalDate dateOfBirth
    ) {}

    public record AdminUpdateUserRequest(
            Boolean isActive,
            Boolean isLocked,
            String role
    ) {}

    public record SetRiskLimitRequest(
            @NotNull Long userId,
            Long instrumentId,
            Integer maxOrderQty,
            java.math.BigDecimal maxOrderValue,
            java.math.BigDecimal maxDailyValue
    ) {}
}

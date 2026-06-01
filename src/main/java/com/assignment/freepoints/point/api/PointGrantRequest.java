package com.assignment.freepoints.point.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PointGrantRequest(
        @NotBlank @Size(max = 64) String userId,
        @NotBlank @Size(max = 100) String transactionNo,
        @Min(1) @Max(100000) long amount,
        Boolean manual,
        @Size(max = 100) String sourceRef,
        @Min(1) @Max(1824) Integer expiryDays
) {
}

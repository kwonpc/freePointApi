package com.assignment.freepoints.point.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PointUseCancelRequest(
        @NotBlank @Size(max = 64) String userId,
        @NotBlank @Size(max = 100) String transactionNo,
        @NotBlank @Size(max = 40) String targetPointKey,
        @Min(1) @Max(100000) long amount,
        @Size(max = 255) String reason
) {
}

package com.assignment.freepoints.point.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PointUseRequest(
        @NotBlank @Size(max = 64) String userId,
        @NotBlank @Size(max = 100) String transactionNo,
        @NotBlank @Size(max = 100) String orderNo,
        long amount
) {
}

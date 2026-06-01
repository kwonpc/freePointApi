package com.assignment.freepoints.point.api;

public record PointBalanceResponse(
        String userId,
        long balance
) {
}

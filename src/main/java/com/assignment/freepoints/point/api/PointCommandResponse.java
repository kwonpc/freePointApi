package com.assignment.freepoints.point.api;

public record PointCommandResponse(
        String operation,
        String userId,
        String transactionNo,
        String pointKey,
        String targetPointKey,
        String orderNo,
        long amount,
        long balance
) {
}

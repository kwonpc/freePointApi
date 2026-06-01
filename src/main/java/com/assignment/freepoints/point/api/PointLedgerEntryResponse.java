package com.assignment.freepoints.point.api;

public record PointLedgerEntryResponse(
        String type,
        String pointKey,
        String targetPointKey,
        String transactionNo,
        String orderNo,
        long amount,
        long remainingAmount,
        String status,
        String grantType,
        String reason,
        String occurredAt
) {
}

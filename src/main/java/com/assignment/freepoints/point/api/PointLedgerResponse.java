package com.assignment.freepoints.point.api;

import java.util.List;

public record PointLedgerResponse(
        String userId,
        long balance,
        List<PointLedgerEntryResponse> entries
) {
}

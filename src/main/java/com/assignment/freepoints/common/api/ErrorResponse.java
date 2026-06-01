package com.assignment.freepoints.common.api;

public record ErrorResponse(
        String code,
        String message
) {
}

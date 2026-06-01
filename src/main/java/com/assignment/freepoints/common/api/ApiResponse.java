package com.assignment.freepoints.common.api;

public record ApiResponse<T>(
        boolean success,
        T data,
        ErrorResponse error
) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static <T> ApiResponse<T> failure(String code, String message) {
        return new ApiResponse<>(false, null, new ErrorResponse(code, message));
    }
}

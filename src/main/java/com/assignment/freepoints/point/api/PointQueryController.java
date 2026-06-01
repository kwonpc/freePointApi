package com.assignment.freepoints.point.api;

import com.assignment.freepoints.common.api.ApiResponse;
import com.assignment.freepoints.point.service.PointQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/points")
@RequiredArgsConstructor
@Tag(name = "Point Query API", description = "포인트 잔액 및 원장 조회 API")
public class PointQueryController {

    private final PointQueryService pointQueryService;

    @GetMapping("/balance")
    @Operation(summary = "잔액 조회", description = "회원의 현재 사용 가능 포인트 잔액을 조회합니다.")
    public ApiResponse<PointBalanceResponse> balance(@RequestParam String userId) {
        return ApiResponse.success(pointQueryService.getBalance(userId));
    }

    @GetMapping("/ledger")
    @Operation(summary = "원장 조회", description = "회원의 적립/취소/사용/사용취소 원장을 조회합니다.")
    public ApiResponse<PointLedgerResponse> ledger(@RequestParam String userId) {
        return ApiResponse.success(pointQueryService.getLedger(userId));
    }
}

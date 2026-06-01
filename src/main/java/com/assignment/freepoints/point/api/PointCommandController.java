package com.assignment.freepoints.point.api;

import com.assignment.freepoints.common.api.ApiResponse;
import com.assignment.freepoints.point.service.PointCommandService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/points")
@RequiredArgsConstructor
@Tag(name = "Point Command API", description = "포인트 적립/취소/사용/사용취소 API")
public class PointCommandController {

    private final PointCommandService pointCommandService;

    @PostMapping("/grants")
    @Operation(summary = "포인트 적립", description = "무료 포인트를 적립합니다. transactionNo 기준 멱등성을 보장합니다.")
    public ApiResponse<PointCommandResponse> grant(@Valid @RequestBody PointGrantRequest request) {
        return ApiResponse.success(pointCommandService.grant(request));
    }

    @PostMapping("/grant-cancels")
    @Operation(summary = "적립 취소", description = "사용되지 않은 적립 건을 취소합니다.")
    public ApiResponse<PointCommandResponse> cancelGrant(@Valid @RequestBody PointGrantCancelRequest request) {
        return ApiResponse.success(pointCommandService.cancelGrant(request));
    }

    @PostMapping("/uses")
    @Operation(summary = "포인트 사용", description = "주문번호와 함께 포인트를 사용합니다.")
    public ApiResponse<PointCommandResponse> use(@Valid @RequestBody PointUseRequest request) {
        return ApiResponse.success(pointCommandService.use(request));
    }

    @PostMapping("/use-cancels")
    @Operation(summary = "포인트 사용 취소", description = "전체 또는 부분 사용취소를 처리합니다.")
    public ApiResponse<PointCommandResponse> cancelUse(@Valid @RequestBody PointUseCancelRequest request) {
        return ApiResponse.success(pointCommandService.cancelUse(request));
    }
}

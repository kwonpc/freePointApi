package com.assignment.freepoints.point.policy;

import com.assignment.freepoints.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PointPolicyReader {

    public static final String MAX_GRANT_PER_TX = "MAX_GRANT_PER_TX";
    public static final String MAX_BALANCE_PER_USER = "MAX_BALANCE_PER_USER";
    public static final String DEFAULT_EXPIRY_DAYS = "DEFAULT_EXPIRY_DAYS";
    public static final String MIN_EXPIRY_DAYS = "MIN_EXPIRY_DAYS";
    public static final String MAX_EXPIRY_DAYS = "MAX_EXPIRY_DAYS";

    private final PointPolicyRepository pointPolicyRepository;

    public long getLong(String code) {
        return pointPolicyRepository.findByPolicyCodeAndActiveTrue(code)
                .map(PointPolicy::getPolicyValue)
                .map(Long::parseLong)
                .orElseThrow(() -> new BusinessException(
                        "POLICY_NOT_FOUND",
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "정책값을 찾을 수 없습니다. code=" + code
                ));
    }
}

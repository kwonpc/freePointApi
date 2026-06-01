package com.assignment.freepoints.point.service;

import com.assignment.freepoints.common.exception.BusinessException;
import com.assignment.freepoints.point.account.PointAccount;
import com.assignment.freepoints.point.account.PointAccountRepository;
import com.assignment.freepoints.point.api.PointCommandResponse;
import com.assignment.freepoints.point.api.PointGrantCancelRequest;
import com.assignment.freepoints.point.api.PointGrantRequest;
import com.assignment.freepoints.point.api.PointUseCancelRequest;
import com.assignment.freepoints.point.api.PointUseRequest;
import com.assignment.freepoints.point.grant.GrantType;
import com.assignment.freepoints.point.grant.PointGrant;
import com.assignment.freepoints.point.grant.PointGrantCancel;
import com.assignment.freepoints.point.grant.PointGrantCancelRepository;
import com.assignment.freepoints.point.grant.PointGrantRepository;
import com.assignment.freepoints.point.grant.PointGrantStatus;
import com.assignment.freepoints.point.policy.PointPolicyReader;
import com.assignment.freepoints.point.transaction.PointTransactionRequest;
import com.assignment.freepoints.point.transaction.PointTransactionRequestRepository;
import com.assignment.freepoints.point.transaction.TransactionType;
import com.assignment.freepoints.point.use.PointUse;
import com.assignment.freepoints.point.use.PointUseAllocation;
import com.assignment.freepoints.point.use.PointUseAllocationRepository;
import com.assignment.freepoints.point.use.PointUseCancel;
import com.assignment.freepoints.point.use.PointUseCancelAllocation;
import com.assignment.freepoints.point.use.PointUseCancelAllocationRepository;
import com.assignment.freepoints.point.use.PointUseCancelRepository;
import com.assignment.freepoints.point.use.PointUseRepository;
import com.assignment.freepoints.point.use.RestoreType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PointCommandService {

    private final PointAccountRepository pointAccountRepository;
    private final PointTransactionRequestRepository pointTransactionRequestRepository;
    private final PointGrantRepository pointGrantRepository;
    private final PointGrantCancelRepository pointGrantCancelRepository;
    private final PointUseRepository pointUseRepository;
    private final PointUseAllocationRepository pointUseAllocationRepository;
    private final PointUseCancelRepository pointUseCancelRepository;
    private final PointUseCancelAllocationRepository pointUseCancelAllocationRepository;
    private final PointPolicyReader pointPolicyReader;
    private final ObjectMapper objectMapper;

    @Transactional
    public PointCommandResponse grant(PointGrantRequest request) {
        PointAccount account = findOrCreateAccountForUpdate(request.userId());
        String requestHash = hash("GRANT", request.userId(), request.transactionNo(), String.valueOf(request.amount()),
                String.valueOf(Boolean.TRUE.equals(request.manual())), String.valueOf(request.expiryDays()), value(request.sourceRef()));

        return handleIdempotentRequest(
                account,
                TransactionType.GRANT,
                request.transactionNo(),
                requestHash,
                request.amount(),
                null,
                null,
                () -> {
                    long maxGrant = pointPolicyReader.getLong(PointPolicyReader.MAX_GRANT_PER_TX);
                    long maxBalance = pointPolicyReader.getLong(PointPolicyReader.MAX_BALANCE_PER_USER);
                    long minExpiryDays = pointPolicyReader.getLong(PointPolicyReader.MIN_EXPIRY_DAYS);
                    long maxExpiryDays = pointPolicyReader.getLong(PointPolicyReader.MAX_EXPIRY_DAYS);
                    long defaultExpiryDays = pointPolicyReader.getLong(PointPolicyReader.DEFAULT_EXPIRY_DAYS);
                    int expiryDays = request.expiryDays() == null ? (int) defaultExpiryDays : request.expiryDays();

                    if (request.amount() > maxGrant) {
                        throw new BusinessException("GRANT_LIMIT_EXCEEDED", HttpStatus.BAD_REQUEST, "1회 최대 적립 한도를 초과했습니다.");
                    }
                    if (expiryDays < minExpiryDays || expiryDays > maxExpiryDays) {
                        throw new BusinessException("INVALID_EXPIRY_DAYS", HttpStatus.BAD_REQUEST, "만료일 범위를 벗어났습니다.");
                    }
                    if (account.getBalance() + request.amount() > maxBalance) {
                        throw new BusinessException("BALANCE_LIMIT_EXCEEDED", HttpStatus.BAD_REQUEST, "개인 최대 보유 한도를 초과했습니다.");
                    }

                    LocalDateTime expiresAt = LocalDateTime.now().plusDays(expiryDays);
                    PointTransactionRequest transactionRequest = pointTransactionRequestRepository.save(PointTransactionRequest.create(
                            account,
                            TransactionType.GRANT,
                            request.transactionNo(),
                            requestHash,
                            request.amount(),
                            null,
                            null
                    ));

                    PointGrant grant = PointGrant.create(
                            newPointKey(),
                            transactionRequest,
                            account,
                            Boolean.TRUE.equals(request.manual()) ? GrantType.MANUAL : GrantType.NORMAL,
                            request.sourceRef(),
                            request.amount(),
                            expiresAt,
                            null
                    );

                    account.increaseBalance(request.amount());
                    pointGrantRepository.save(grant);

                    PointCommandResponse response = new PointCommandResponse(
                            "GRANT",
                            account.getUserId(),
                            request.transactionNo(),
                            grant.getPointKey(),
                            null,
                            null,
                            request.amount(),
                            account.getBalance()
                    );
                    transactionRequest.markSucceeded(grant.getPointKey(), toSnapshot(response));
                    return response;
                }
        );
    }

    @Transactional
    public PointCommandResponse cancelGrant(PointGrantCancelRequest request) {
        PointAccount account = findExistingAccountForUpdate(request.userId());
        String requestHash = hash("GRANT_CANCEL", request.userId(), request.transactionNo(), request.targetPointKey(), value(request.reason()));

        return handleIdempotentRequest(
                account,
                TransactionType.GRANT_CANCEL,
                request.transactionNo(),
                requestHash,
                null,
                null,
                request.targetPointKey(),
                () -> {
                    PointGrant grant = pointGrantRepository.findByPointKey(request.targetPointKey())
                            .orElseThrow(() -> new BusinessException("POINT_NOT_FOUND", HttpStatus.NOT_FOUND, "적립 포인트를 찾을 수 없습니다."));

                    validateAccountOwnership(account, grant.getAccount().getId());
                    if (pointUseAllocationRepository.existsByGrant_Id(grant.getId())) {
                        throw new BusinessException("GRANT_ALREADY_USED", HttpStatus.BAD_REQUEST, "이미 사용 이력이 있는 적립건은 취소할 수 없습니다.");
                    }
                    if (grant.getStatus() == PointGrantStatus.CANCELED) {
                        throw new BusinessException("GRANT_ALREADY_CANCELED", HttpStatus.BAD_REQUEST, "이미 취소된 적립건입니다.");
                    }

                    PointTransactionRequest transactionRequest = pointTransactionRequestRepository.save(PointTransactionRequest.create(
                            account,
                            TransactionType.GRANT_CANCEL,
                            request.transactionNo(),
                            requestHash,
                            null,
                            null,
                            request.targetPointKey()
                    ));

                    grant.cancel();
                    account.decreaseBalance(grant.getGrantedAmount());
                    PointGrantCancel grantCancel = PointGrantCancel.create(
                            newPointKey(),
                            transactionRequest,
                            grant,
                            grant.getGrantedAmount(),
                            request.reason()
                    );
                    pointGrantCancelRepository.save(grantCancel);

                    PointCommandResponse response = new PointCommandResponse(
                            "GRANT_CANCEL",
                            account.getUserId(),
                            request.transactionNo(),
                            grantCancel.getPointKey(),
                            grant.getPointKey(),
                            null,
                            grant.getGrantedAmount(),
                            account.getBalance()
                    );
                    transactionRequest.markSucceeded(grantCancel.getPointKey(), toSnapshot(response));
                    return response;
                }
        );
    }

    @Transactional
    public PointCommandResponse use(PointUseRequest request) {
        PointAccount account = findExistingAccountForUpdate(request.userId());
        String requestHash = hash("USE", request.userId(), request.transactionNo(), request.orderNo(), String.valueOf(request.amount()));

        return handleIdempotentRequest(
                account,
                TransactionType.USE,
                request.transactionNo(),
                requestHash,
                request.amount(),
                request.orderNo(),
                null,
                () -> {
                    if (account.getBalance() < request.amount()) {
                        throw new BusinessException("INSUFFICIENT_BALANCE", HttpStatus.BAD_REQUEST, "포인트 잔액이 부족합니다.");
                    }

                    List<PointGrant> grants = pointGrantRepository.findAvailableGrants(account.getId(), LocalDateTime.now());
                    long remaining = request.amount();
                    long available = grants.stream().mapToLong(PointGrant::getRemainingAmount).sum();
                    if (available < request.amount()) {
                        throw new BusinessException("INSUFFICIENT_BALANCE", HttpStatus.BAD_REQUEST, "사용 가능한 포인트가 부족합니다.");
                    }

                    PointTransactionRequest transactionRequest = pointTransactionRequestRepository.save(PointTransactionRequest.create(
                            account,
                            TransactionType.USE,
                            request.transactionNo(),
                            requestHash,
                            request.amount(),
                            request.orderNo(),
                            null
                    ));

                    PointUse pointUse = PointUse.create(newPointKey(), transactionRequest, account, request.orderNo(), request.amount());
                    pointUseRepository.save(pointUse);

                    for (PointGrant grant : grants) {
                        if (remaining == 0) {
                            break;
                        }
                        long consumed = Math.min(remaining, grant.getRemainingAmount());
                        grant.consume(consumed);
                        pointUseAllocationRepository.save(PointUseAllocation.create(pointUse, grant, consumed));
                        remaining -= consumed;
                    }

                    account.decreaseBalance(request.amount());

                    PointCommandResponse response = new PointCommandResponse(
                            "USE",
                            account.getUserId(),
                            request.transactionNo(),
                            pointUse.getPointKey(),
                            null,
                            request.orderNo(),
                            request.amount(),
                            account.getBalance()
                    );
                    transactionRequest.markSucceeded(pointUse.getPointKey(), toSnapshot(response));
                    return response;
                }
        );
    }

    @Transactional
    public PointCommandResponse cancelUse(PointUseCancelRequest request) {
        PointAccount account = findExistingAccountForUpdate(request.userId());
        String requestHash = hash("USE_CANCEL", request.userId(), request.transactionNo(), request.targetPointKey(),
                String.valueOf(request.amount()), value(request.reason()));

        return handleIdempotentRequest(
                account,
                TransactionType.USE_CANCEL,
                request.transactionNo(),
                requestHash,
                request.amount(),
                null,
                request.targetPointKey(),
                () -> {
                    PointUse pointUse = pointUseRepository.findByPointKey(request.targetPointKey())
                            .orElseThrow(() -> new BusinessException("POINT_NOT_FOUND", HttpStatus.NOT_FOUND, "사용 포인트를 찾을 수 없습니다."));

                    validateAccountOwnership(account, pointUse.getAccount().getId());
                    if (pointUse.getCancelableAmount() < request.amount()) {
                        throw new BusinessException("INVALID_CANCEL_AMOUNT", HttpStatus.BAD_REQUEST, "취소 가능한 금액을 초과했습니다.");
                    }

                    PointTransactionRequest transactionRequest = pointTransactionRequestRepository.save(PointTransactionRequest.create(
                            account,
                            TransactionType.USE_CANCEL,
                            request.transactionNo(),
                            requestHash,
                            request.amount(),
                            null,
                            request.targetPointKey()
                    ));

                    PointUseCancel pointUseCancel = PointUseCancel.create(
                            newPointKey(),
                            transactionRequest,
                            pointUse,
                            request.amount(),
                            request.reason()
                    );
                    pointUseCancelRepository.save(pointUseCancel);

                    long remaining = request.amount();
                    List<PointUseAllocation> allocations = pointUseAllocationRepository.findByPointUse_IdOrderByIdAsc(pointUse.getId());
                    long defaultExpiryDays = pointPolicyReader.getLong(PointPolicyReader.DEFAULT_EXPIRY_DAYS);

                    for (PointUseAllocation allocation : allocations) {
                        if (remaining == 0) {
                            break;
                        }
                        long cancelAmount = Math.min(remaining, allocation.remainingCancelableAmount());
                        if (cancelAmount == 0) {
                            continue;
                        }

                        allocation.cancel(cancelAmount);
                        PointGrant sourceGrant = allocation.getGrant();
                        PointGrant restoredGrant = null;
                        RestoreType restoreType;

                        if (sourceGrant.isExpiredAt(LocalDateTime.now())) {
                            sourceGrant.markExpired();
                            restoredGrant = PointGrant.create(
                                    newPointKey(),
                                    transactionRequest,
                                    account,
                                    GrantType.REGRANT_FROM_USE_CANCEL,
                                    sourceGrant.getPointKey(),
                                    cancelAmount,
                                    LocalDateTime.now().plusDays(defaultExpiryDays),
                                    pointUseCancel
                            );
                            pointGrantRepository.save(restoredGrant);
                            restoreType = RestoreType.REGRANT_NEW_POINT;
                        } else {
                            sourceGrant.restore(cancelAmount);
                            restoreType = RestoreType.RETURN_TO_GRANT;
                        }

                        pointUseCancelAllocationRepository.save(PointUseCancelAllocation.create(
                                pointUseCancel,
                                allocation,
                                cancelAmount,
                                restoreType,
                                restoredGrant
                        ));
                        remaining -= cancelAmount;
                    }

                    pointUse.cancel(request.amount());
                    account.increaseBalance(request.amount());

                    PointCommandResponse response = new PointCommandResponse(
                            "USE_CANCEL",
                            account.getUserId(),
                            request.transactionNo(),
                            pointUseCancel.getPointKey(),
                            pointUse.getPointKey(),
                            pointUse.getOrderNo(),
                            request.amount(),
                            account.getBalance()
                    );
                    transactionRequest.markSucceeded(pointUseCancel.getPointKey(), toSnapshot(response));
                    return response;
                }
        );
    }

    private PointAccount findOrCreateAccountForUpdate(String userId) {
        return pointAccountRepository.findByUserIdForUpdate(userId)
                .orElseGet(() -> createAccount(userId));
    }

    private PointAccount createAccount(String userId) {
        try {
            return pointAccountRepository.saveAndFlush(PointAccount.create(userId));
        } catch (DataIntegrityViolationException ignored) {
            return pointAccountRepository.findByUserIdForUpdate(userId)
                    .orElseThrow(() -> new BusinessException("ACCOUNT_LOCK_FAILED", HttpStatus.CONFLICT, "계정 생성 충돌이 발생했습니다."));
        }
    }

    private PointAccount findExistingAccountForUpdate(String userId) {
        return pointAccountRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException("ACCOUNT_NOT_FOUND", HttpStatus.NOT_FOUND, "회원 포인트 계정을 찾을 수 없습니다."));
    }

    private void validateAccountOwnership(PointAccount account, Long ownerAccountId) {
        if (!account.getId().equals(ownerAccountId)) {
            throw new BusinessException("POINT_OWNER_MISMATCH", HttpStatus.BAD_REQUEST, "다른 회원의 포인트는 처리할 수 없습니다.");
        }
    }

    private PointCommandResponse handleIdempotentRequest(
            PointAccount account,
            TransactionType transactionType,
            String transactionNo,
            String requestHash,
            Long requestAmount,
            String requestOrderNo,
            String targetPointKey,
            BusinessAction action
    ) {
        PointTransactionRequest existing = pointTransactionRequestRepository
                .findByAccount_IdAndTransactionTypeAndTransactionNo(account.getId(), transactionType, transactionNo)
                .orElse(null);

        if (existing != null) {
            if (!existing.matchesHash(requestHash)) {
                throw new BusinessException("IDEMPOTENCY_CONFLICT", HttpStatus.CONFLICT, "동일 transactionNo 에 서로 다른 요청이 들어왔습니다.");
            }
            return fromSnapshot(existing.getResponseSnapshot());
        }

        return action.execute();
    }

    private String toSnapshot(PointCommandResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("SNAPSHOT_SERIALIZE_FAILED", HttpStatus.INTERNAL_SERVER_ERROR, "응답 저장에 실패했습니다.");
        }
    }

    private PointCommandResponse fromSnapshot(String snapshot) {
        try {
            return objectMapper.readValue(snapshot, PointCommandResponse.class);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("SNAPSHOT_DESERIALIZE_FAILED", HttpStatus.INTERNAL_SERVER_ERROR, "저장된 응답 복원에 실패했습니다.");
        }
    }

    private String hash(String... values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(String.join("|", values).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new BusinessException("HASH_ERROR", HttpStatus.INTERNAL_SERVER_ERROR, "요청 해시 생성에 실패했습니다.");
        }
    }

    private String newPointKey() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String value(String input) {
        return input == null ? "" : input;
    }

    @FunctionalInterface
    private interface BusinessAction {
        PointCommandResponse execute();
    }
}

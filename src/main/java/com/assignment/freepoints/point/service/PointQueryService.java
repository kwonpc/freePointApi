package com.assignment.freepoints.point.service;

import com.assignment.freepoints.common.exception.BusinessException;
import com.assignment.freepoints.point.account.PointAccount;
import com.assignment.freepoints.point.account.PointAccountRepository;
import com.assignment.freepoints.point.api.PointBalanceResponse;
import com.assignment.freepoints.point.api.PointLedgerEntryResponse;
import com.assignment.freepoints.point.api.PointLedgerResponse;
import com.assignment.freepoints.point.grant.PointGrant;
import com.assignment.freepoints.point.grant.PointGrantCancel;
import com.assignment.freepoints.point.grant.PointGrantCancelRepository;
import com.assignment.freepoints.point.grant.PointGrantRepository;
import com.assignment.freepoints.point.use.PointUse;
import com.assignment.freepoints.point.use.PointUseCancel;
import com.assignment.freepoints.point.use.PointUseCancelRepository;
import com.assignment.freepoints.point.use.PointUseRepository;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PointQueryService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final PointAccountRepository pointAccountRepository;
    private final PointGrantRepository pointGrantRepository;
    private final PointGrantCancelRepository pointGrantCancelRepository;
    private final PointUseRepository pointUseRepository;
    private final PointUseCancelRepository pointUseCancelRepository;

    @Transactional(readOnly = true)
    public PointBalanceResponse getBalance(String userId) {
        PointAccount account = findAccount(userId);
        return new PointBalanceResponse(account.getUserId(), account.getBalance());
    }

    @Transactional(readOnly = true)
    public PointLedgerResponse getLedger(String userId) {
        PointAccount account = findAccount(userId);
        List<PointLedgerEntryResponse> entries = new ArrayList<>();

        for (PointGrant grant : pointGrantRepository.findByAccount_IdOrderByIdAsc(account.getId())) {
            entries.add(new PointLedgerEntryResponse(
                    "GRANT",
                    grant.getPointKey(),
                    null,
                    grant.getTransactionRequest().getTransactionNo(),
                    null,
                    grant.getGrantedAmount(),
                    grant.getRemainingAmount(),
                    grant.getStatus().name(),
                    grant.getGrantType().name(),
                    null,
                    DATE_TIME_FORMATTER.format(grant.getCreatedAt())
            ));
        }

        for (PointGrantCancel cancel : pointGrantCancelRepository.findAllByAccountId(account.getId())) {
            entries.add(new PointLedgerEntryResponse(
                    "GRANT_CANCEL",
                    cancel.getPointKey(),
                    cancel.getGrant().getPointKey(),
                    cancel.getTransactionRequest().getTransactionNo(),
                    null,
                    cancel.getCanceledAmount(),
                    0L,
                    "CANCELED",
                    null,
                    cancel.getReason(),
                    DATE_TIME_FORMATTER.format(cancel.getCreatedAt())
            ));
        }

        for (PointUse pointUse : pointUseRepository.findByAccount_IdOrderByIdAsc(account.getId())) {
            entries.add(new PointLedgerEntryResponse(
                    "USE",
                    pointUse.getPointKey(),
                    null,
                    pointUse.getTransactionRequest().getTransactionNo(),
                    pointUse.getOrderNo(),
                    pointUse.getUsedAmount(),
                    pointUse.getCancelableAmount(),
                    pointUse.getStatus().name(),
                    null,
                    null,
                    DATE_TIME_FORMATTER.format(pointUse.getCreatedAt())
            ));
        }

        for (PointUseCancel cancel : pointUseCancelRepository.findAllByAccountId(account.getId())) {
            entries.add(new PointLedgerEntryResponse(
                    "USE_CANCEL",
                    cancel.getPointKey(),
                    cancel.getPointUse().getPointKey(),
                    cancel.getTransactionRequest().getTransactionNo(),
                    cancel.getPointUse().getOrderNo(),
                    cancel.getCanceledAmount(),
                    0L,
                    "CANCELED",
                    null,
                    cancel.getReason(),
                    DATE_TIME_FORMATTER.format(cancel.getCreatedAt())
            ));
        }

        entries.sort(Comparator.comparing(PointLedgerEntryResponse::occurredAt).thenComparing(PointLedgerEntryResponse::pointKey));
        return new PointLedgerResponse(account.getUserId(), account.getBalance(), entries);
    }

    private PointAccount findAccount(String userId) {
        return pointAccountRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException("ACCOUNT_NOT_FOUND", HttpStatus.NOT_FOUND, "회원 포인트 계정을 찾을 수 없습니다."));
    }
}

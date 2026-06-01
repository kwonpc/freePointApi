package com.assignment.freepoints.point.grant;

import com.assignment.freepoints.common.entity.BaseAuditEntity;
import com.assignment.freepoints.point.account.PointAccount;
import com.assignment.freepoints.point.transaction.PointTransactionRequest;
import com.assignment.freepoints.point.use.PointUseCancel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "point_grant",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_point_grant_key", columnNames = "point_key")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointGrant extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "point_key", nullable = false, length = 40)
    private String pointKey;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_request_id", nullable = false)
    private PointTransactionRequest transactionRequest;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private PointAccount account;

    @Enumerated(EnumType.STRING)
    @Column(name = "grant_type", nullable = false, length = 30)
    private GrantType grantType;

    @Column(name = "source_ref", length = 100)
    private String sourceRef;

    @Column(name = "granted_amount", nullable = false)
    private long grantedAmount;

    @Column(name = "remaining_amount", nullable = false)
    private long remainingAmount;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PointGrantStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_use_cancel_id")
    private PointUseCancel originalUseCancel;

    private PointGrant(
            String pointKey,
            PointTransactionRequest transactionRequest,
            PointAccount account,
            GrantType grantType,
            String sourceRef,
            long grantedAmount,
            LocalDateTime expiresAt,
            PointUseCancel originalUseCancel
    ) {
        this.pointKey = pointKey;
        this.transactionRequest = transactionRequest;
        this.account = account;
        this.grantType = grantType;
        this.sourceRef = sourceRef;
        this.grantedAmount = grantedAmount;
        this.remainingAmount = grantedAmount;
        this.expiresAt = expiresAt;
        this.status = PointGrantStatus.ACTIVE;
        this.originalUseCancel = originalUseCancel;
    }

    public static PointGrant create(
            String pointKey,
            PointTransactionRequest transactionRequest,
            PointAccount account,
            GrantType grantType,
            String sourceRef,
            long grantedAmount,
            LocalDateTime expiresAt,
            PointUseCancel originalUseCancel
    ) {
        return new PointGrant(
                pointKey,
                transactionRequest,
                account,
                grantType,
                sourceRef,
                grantedAmount,
                expiresAt,
                originalUseCancel
        );
    }

    public void consume(long amount) {
        this.remainingAmount -= amount;
        this.status = this.remainingAmount == 0 ? PointGrantStatus.DEPLETED : PointGrantStatus.ACTIVE;
    }

    public void restore(long amount) {
        this.remainingAmount += amount;
        this.status = isExpiredAt(LocalDateTime.now()) ? PointGrantStatus.EXPIRED : PointGrantStatus.ACTIVE;
    }

    public void cancel() {
        this.remainingAmount = 0;
        this.status = PointGrantStatus.CANCELED;
    }

    public boolean isExpiredAt(LocalDateTime baseTime) {
        return !this.expiresAt.isAfter(baseTime);
    }

    public void markExpired() {
        this.status = PointGrantStatus.EXPIRED;
    }
}

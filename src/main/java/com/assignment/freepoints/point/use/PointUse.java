package com.assignment.freepoints.point.use;

import com.assignment.freepoints.common.entity.BaseAuditEntity;
import com.assignment.freepoints.point.account.PointAccount;
import com.assignment.freepoints.point.transaction.PointTransactionRequest;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "point_use",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_point_use_key", columnNames = "point_key"),
                @UniqueConstraint(name = "uk_point_use_tx_request", columnNames = "transaction_request_id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointUse extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "point_key", nullable = false, length = 40)
    private String pointKey;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_request_id", nullable = false)
    private PointTransactionRequest transactionRequest;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private PointAccount account;

    @Column(name = "order_no", nullable = false, length = 100)
    private String orderNo;

    @Column(name = "used_amount", nullable = false)
    private long usedAmount;

    @Column(name = "cancelable_amount", nullable = false)
    private long cancelableAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PointUseStatus status;

    private PointUse(
            String pointKey,
            PointTransactionRequest transactionRequest,
            PointAccount account,
            String orderNo,
            long usedAmount
    ) {
        this.pointKey = pointKey;
        this.transactionRequest = transactionRequest;
        this.account = account;
        this.orderNo = orderNo;
        this.usedAmount = usedAmount;
        this.cancelableAmount = usedAmount;
        this.status = PointUseStatus.USED;
    }

    public static PointUse create(
            String pointKey,
            PointTransactionRequest transactionRequest,
            PointAccount account,
            String orderNo,
            long usedAmount
    ) {
        return new PointUse(pointKey, transactionRequest, account, orderNo, usedAmount);
    }

    public void cancel(long amount) {
        this.cancelableAmount -= amount;
        this.status = this.cancelableAmount == 0 ? PointUseStatus.CANCELED : PointUseStatus.PARTIALLY_CANCELED;
    }
}

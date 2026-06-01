package com.assignment.freepoints.point.transaction;

import com.assignment.freepoints.common.entity.BaseAuditEntity;
import com.assignment.freepoints.point.account.PointAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "point_transaction_request",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ptr_account_type_tx_no",
                columnNames = {"account_id", "transaction_type", "transaction_no"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointTransactionRequest extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private PointAccount account;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 30)
    private TransactionType transactionType;

    @Column(name = "transaction_no", nullable = false, length = 100)
    private String transactionNo;

    @Column(name = "request_hash", nullable = false, length = 128)
    private String requestHash;

    @Column(name = "request_amount")
    private Long requestAmount;

    @Column(name = "request_order_no", length = 100)
    private String requestOrderNo;

    @Column(name = "target_point_key", length = 40)
    private String targetPointKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionRequestStatus status;

    @Column(name = "result_point_key", length = 40)
    private String resultPointKey;

    @Column(name = "error_code", length = 50)
    private String errorCode;

    @Lob
    @Column(name = "response_snapshot")
    private String responseSnapshot;

    private PointTransactionRequest(
            PointAccount account,
            TransactionType transactionType,
            String transactionNo,
            String requestHash,
            Long requestAmount,
            String requestOrderNo,
            String targetPointKey
    ) {
        this.account = account;
        this.transactionType = transactionType;
        this.transactionNo = transactionNo;
        this.requestHash = requestHash;
        this.requestAmount = requestAmount;
        this.requestOrderNo = requestOrderNo;
        this.targetPointKey = targetPointKey;
        this.status = TransactionRequestStatus.PROCESSING;
    }

    public static PointTransactionRequest create(
            PointAccount account,
            TransactionType transactionType,
            String transactionNo,
            String requestHash,
            Long requestAmount,
            String requestOrderNo,
            String targetPointKey
    ) {
        return new PointTransactionRequest(
                account,
                transactionType,
                transactionNo,
                requestHash,
                requestAmount,
                requestOrderNo,
                targetPointKey
        );
    }

    public boolean matchesHash(String requestHash) {
        return this.requestHash.equals(requestHash);
    }

    public void markSucceeded(String resultPointKey, String responseSnapshot) {
        this.status = TransactionRequestStatus.SUCCEEDED;
        this.resultPointKey = resultPointKey;
        this.responseSnapshot = responseSnapshot;
        this.errorCode = null;
    }
}

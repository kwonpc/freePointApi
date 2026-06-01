package com.assignment.freepoints.point.grant;

import com.assignment.freepoints.point.transaction.PointTransactionRequest;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "point_grant_cancel",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_point_grant_cancel_key", columnNames = "point_key"),
                @UniqueConstraint(name = "uk_point_grant_cancel_tx_request", columnNames = "transaction_request_id"),
                @UniqueConstraint(name = "uk_point_grant_cancel_grant", columnNames = "grant_id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointGrantCancel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "point_key", nullable = false, length = 40)
    private String pointKey;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_request_id", nullable = false)
    private PointTransactionRequest transactionRequest;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grant_id", nullable = false)
    private PointGrant grant;

    @Column(name = "canceled_amount", nullable = false)
    private long canceledAmount;

    @Column(length = 255)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private LocalDateTime createdAt;

    private PointGrantCancel(
            String pointKey,
            PointTransactionRequest transactionRequest,
            PointGrant grant,
            long canceledAmount,
            String reason
    ) {
        this.pointKey = pointKey;
        this.transactionRequest = transactionRequest;
        this.grant = grant;
        this.canceledAmount = canceledAmount;
        this.reason = reason;
    }

    public static PointGrantCancel create(
            String pointKey,
            PointTransactionRequest transactionRequest,
            PointGrant grant,
            long canceledAmount,
            String reason
    ) {
        return new PointGrantCancel(pointKey, transactionRequest, grant, canceledAmount, reason);
    }
}

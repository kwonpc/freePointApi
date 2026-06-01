package com.assignment.freepoints.point.use;

import com.assignment.freepoints.point.transaction.PointTransactionRequest;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
        name = "point_use_cancel",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_point_use_cancel_key", columnNames = "point_key"),
                @UniqueConstraint(name = "uk_point_use_cancel_tx_request", columnNames = "transaction_request_id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointUseCancel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "point_key", nullable = false, length = 40)
    private String pointKey;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_request_id", nullable = false)
    private PointTransactionRequest transactionRequest;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "use_id", nullable = false)
    private PointUse pointUse;

    @Column(name = "canceled_amount", nullable = false)
    private long canceledAmount;

    @Column(length = 255)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private LocalDateTime createdAt;

    private PointUseCancel(
            String pointKey,
            PointTransactionRequest transactionRequest,
            PointUse pointUse,
            long canceledAmount,
            String reason
    ) {
        this.pointKey = pointKey;
        this.transactionRequest = transactionRequest;
        this.pointUse = pointUse;
        this.canceledAmount = canceledAmount;
        this.reason = reason;
    }

    public static PointUseCancel create(
            String pointKey,
            PointTransactionRequest transactionRequest,
            PointUse pointUse,
            long canceledAmount,
            String reason
    ) {
        return new PointUseCancel(pointKey, transactionRequest, pointUse, canceledAmount, reason);
    }
}

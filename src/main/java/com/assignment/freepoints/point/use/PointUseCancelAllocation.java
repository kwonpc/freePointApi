package com.assignment.freepoints.point.use;

import com.assignment.freepoints.point.grant.PointGrant;
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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "point_use_cancel_allocation")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointUseCancelAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "use_cancel_id", nullable = false)
    private PointUseCancel pointUseCancel;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "use_allocation_id", nullable = false)
    private PointUseAllocation useAllocation;

    @Column(name = "canceled_amount", nullable = false)
    private long canceledAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "restore_type", nullable = false, length = 30)
    private RestoreType restoreType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restored_grant_id")
    private PointGrant restoredGrant;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private LocalDateTime createdAt;

    private PointUseCancelAllocation(
            PointUseCancel pointUseCancel,
            PointUseAllocation useAllocation,
            long canceledAmount,
            RestoreType restoreType,
            PointGrant restoredGrant
    ) {
        this.pointUseCancel = pointUseCancel;
        this.useAllocation = useAllocation;
        this.canceledAmount = canceledAmount;
        this.restoreType = restoreType;
        this.restoredGrant = restoredGrant;
    }

    public static PointUseCancelAllocation create(
            PointUseCancel pointUseCancel,
            PointUseAllocation useAllocation,
            long canceledAmount,
            RestoreType restoreType,
            PointGrant restoredGrant
    ) {
        return new PointUseCancelAllocation(pointUseCancel, useAllocation, canceledAmount, restoreType, restoredGrant);
    }
}

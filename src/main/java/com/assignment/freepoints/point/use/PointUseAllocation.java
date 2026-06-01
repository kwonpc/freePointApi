package com.assignment.freepoints.point.use;

import com.assignment.freepoints.common.entity.BaseAuditEntity;
import com.assignment.freepoints.point.grant.PointGrant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "point_use_allocation")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointUseAllocation extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "use_id", nullable = false)
    private PointUse pointUse;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grant_id", nullable = false)
    private PointGrant grant;

    @Column(name = "allocated_amount", nullable = false)
    private long allocatedAmount;

    @Column(name = "canceled_amount", nullable = false)
    private long canceledAmount;

    private PointUseAllocation(PointUse pointUse, PointGrant grant, long allocatedAmount) {
        this.pointUse = pointUse;
        this.grant = grant;
        this.allocatedAmount = allocatedAmount;
        this.canceledAmount = 0L;
    }

    public static PointUseAllocation create(PointUse pointUse, PointGrant grant, long allocatedAmount) {
        return new PointUseAllocation(pointUse, grant, allocatedAmount);
    }

    public long remainingCancelableAmount() {
        return allocatedAmount - canceledAmount;
    }

    public void cancel(long amount) {
        this.canceledAmount += amount;
    }
}

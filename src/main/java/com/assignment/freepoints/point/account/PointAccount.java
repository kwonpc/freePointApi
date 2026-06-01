package com.assignment.freepoints.point.account;

import com.assignment.freepoints.common.entity.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "point_account",
        uniqueConstraints = @UniqueConstraint(name = "uk_point_account_user_id", columnNames = "user_id")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointAccount extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(nullable = false)
    private long balance;

    @Version
    @Column(nullable = false)
    private long version;

    private PointAccount(String userId) {
        this.userId = userId;
        this.balance = 0L;
    }

    public static PointAccount create(String userId) {
        return new PointAccount(userId);
    }

    public void increaseBalance(long amount) {
        this.balance += amount;
    }

    public void decreaseBalance(long amount) {
        this.balance -= amount;
    }
}

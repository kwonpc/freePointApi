package com.assignment.freepoints.point.policy;

import com.assignment.freepoints.common.entity.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "point_policy",
        uniqueConstraints = @UniqueConstraint(name = "uk_point_policy_code", columnNames = "policy_code")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointPolicy extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "policy_code", nullable = false, length = 50)
    private String policyCode;

    @Column(name = "policy_value", nullable = false, length = 100)
    private String policyValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "value_type", nullable = false, length = 20)
    private PolicyValueType valueType;

    @Column(length = 255)
    private String description;

    @Column(nullable = false)
    private boolean active;
}

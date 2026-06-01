package com.assignment.freepoints.point.policy;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PointPolicyRepository extends JpaRepository<PointPolicy, Long> {

    Optional<PointPolicy> findByPolicyCodeAndActiveTrue(String policyCode);
}

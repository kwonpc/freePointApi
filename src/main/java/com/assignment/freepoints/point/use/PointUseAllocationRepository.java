package com.assignment.freepoints.point.use;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PointUseAllocationRepository extends JpaRepository<PointUseAllocation, Long> {

    boolean existsByGrant_Id(Long grantId);

    List<PointUseAllocation> findByPointUse_IdOrderByIdAsc(Long useId);
}

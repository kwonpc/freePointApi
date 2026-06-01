package com.assignment.freepoints.point.use;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PointUseRepository extends JpaRepository<PointUse, Long> {

    Optional<PointUse> findByPointKey(String pointKey);
    List<PointUse> findByAccount_IdOrderByIdAsc(Long accountId);
}

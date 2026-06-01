package com.assignment.freepoints.point.use;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PointUseCancelRepository extends JpaRepository<PointUseCancel, Long> {

    @Query("""
            select uc
            from PointUseCancel uc
            join fetch uc.pointUse pu
            where pu.account.id = :accountId
            order by uc.id asc
            """)
    List<PointUseCancel> findAllByAccountId(@Param("accountId") Long accountId);
}

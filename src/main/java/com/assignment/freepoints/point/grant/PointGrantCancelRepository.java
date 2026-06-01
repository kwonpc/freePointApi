package com.assignment.freepoints.point.grant;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PointGrantCancelRepository extends JpaRepository<PointGrantCancel, Long> {

    @Query("""
            select gc
            from PointGrantCancel gc
            join fetch gc.grant g
            where g.account.id = :accountId
            order by gc.id asc
            """)
    List<PointGrantCancel> findAllByAccountId(@Param("accountId") Long accountId);
}

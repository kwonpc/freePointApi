package com.assignment.freepoints.point.grant;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PointGrantRepository extends JpaRepository<PointGrant, Long> {

    Optional<PointGrant> findByPointKey(String pointKey);
    List<PointGrant> findByAccount_IdOrderByIdAsc(Long accountId);

    @Query("""
            select g
            from PointGrant g
            where g.account.id = :accountId
              and g.status = com.assignment.freepoints.point.grant.PointGrantStatus.ACTIVE
              and g.remainingAmount > 0
              and g.expiresAt > :baseTime
            order by case when g.grantType = com.assignment.freepoints.point.grant.GrantType.MANUAL then 0 else 1 end,
                     g.expiresAt asc,
                     g.id asc
            """)
    List<PointGrant> findAvailableGrants(@Param("accountId") Long accountId, @Param("baseTime") LocalDateTime baseTime);
}

package com.assignment.freepoints.point.transaction;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PointTransactionRequestRepository extends JpaRepository<PointTransactionRequest, Long> {

    Optional<PointTransactionRequest> findByAccount_IdAndTransactionTypeAndTransactionNo(
            Long accountId,
            TransactionType transactionType,
            String transactionNo
    );
}

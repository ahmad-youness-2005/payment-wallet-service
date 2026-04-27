package com.ahmad.wallet.repository;

import com.ahmad.wallet.domain.Transfer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransferRepository extends JpaRepository<Transfer, UUID> {
    Optional<Transfer> findByIdempotencyKeyAndSenderWalletId(String idempotencyKey, UUID senderWalletId);
    List<Transfer> findBySenderWalletIdOrReceiverWalletIdOrderByCreatedAtDesc(UUID senderWalletId, UUID receiverWalletId);
}

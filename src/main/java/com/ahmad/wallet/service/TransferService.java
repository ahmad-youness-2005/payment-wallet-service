package com.ahmad.wallet.service;

import com.ahmad.wallet.api.dto.TransferDto;
import com.ahmad.wallet.domain.*;
import com.ahmad.wallet.exception.ApiException;
import com.ahmad.wallet.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransferService {
    private final UserAccountRepository userRepository;
    private final WalletRepository walletRepository;
    private final TransferRepository transferRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final AuditEventRepository auditEventRepository;
    private final WalletTransactionRepository walletTransactionRepository;

    @Value("${app.transfer.daily-limit}")
    private BigDecimal dailyLimit;

    @Transactional
    public TransferDto.TransferResponse transfer(String senderEmail, TransferDto.TransferRequest request) {
        validate(request);
        UserAccount sender = userRepository.findByEmail(senderEmail)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Sender not found"));
        Wallet senderWallet = walletRepository.findFirstByUserIdAndStatusOrderByCreatedAtDesc(sender.getId(), WalletStatus.ACTIVE)
                .orElseThrow(() -> new ApiException("Sender wallet not found"));

        UserAccount receiver = userRepository.findById(request.receiverUserId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Receiver not found"));
        if (sender.getId().equals(receiver.getId())) {
            throw new ApiException("Cannot transfer to your own account");
        }
        Wallet receiverWallet = walletRepository.findFirstByUserIdAndStatusOrderByCreatedAtDesc(receiver.getId(), WalletStatus.ACTIVE)
                .orElseThrow(() -> new ApiException("Receiver wallet not found"));

        boolean senderFirst = senderWallet.getId().compareTo(receiverWallet.getId()) < 0;
        UUID firstId = senderFirst ? senderWallet.getId() : receiverWallet.getId();
        UUID secondId = senderFirst ? receiverWallet.getId() : senderWallet.getId();
        Wallet first = walletRepository.lockById(firstId).orElseThrow();
        Wallet second = walletRepository.lockById(secondId).orElseThrow();
        Wallet from = senderFirst ? first : second;
        Wallet to = senderFirst ? second : first;

        BigDecimal amount = request.amount();
        resetDailyCounterIfNeeded(from);
        BigDecimal newDaily = from.getDailyTransferredAmount().add(amount);

        if (newDaily.compareTo(dailyLimit) > 0) {
            recordFailedTransfer(from, amount, receiver.getId(), "Daily transfer limit exceeded");
            throw new ApiException("Daily transfer limit exceeded");
        }
        if (from.getAvailableBalance().compareTo(amount) < 0) {
            recordFailedTransfer(from, amount, receiver.getId(), "Insufficient balance");
            throw new ApiException("Insufficient balance");
        }

        from.setAvailableBalance(from.getAvailableBalance().subtract(amount));
        from.setDailyTransferredAmount(newDaily);
        from.setLastTransferDate(LocalDate.now());
        to.setAvailableBalance(to.getAvailableBalance().add(amount));
        walletRepository.save(from);
        walletRepository.save(to);

        Transfer transfer = new Transfer();
        transfer.setReference("TXN-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase());
        transfer.setAmount(amount);
        transfer.setSenderWallet(from);
        transfer.setReceiverWallet(to);
        transferRepository.save(transfer);

        saveEntry(transfer, from, LedgerEntryType.DEBIT, amount, from.getAvailableBalance());
        saveEntry(transfer, to, LedgerEntryType.CREDIT, amount, to.getAvailableBalance());
        recordSuccess(from, transfer, WalletOperationType.MONEY_TRANSFERRED, amount, receiver.getId());
        recordSuccess(to, transfer, WalletOperationType.MONEY_RECEIVED, amount, sender.getId());
        saveAudit("TRANSFER_SUCCESS", sender.getId(),
                "Transfer " + transfer.getReference() + " sent to user " + receiver.getId());
        return toResponse(transfer);
    }

    public TransferDto.TransferResponse getTransfer(UUID id, String requesterEmail) {
        UserAccount requester = userRepository.findByEmail(requesterEmail)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Requester not found"));
        Transfer transfer = transferRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Transfer not found"));
        UUID requesterId = requester.getId();
        boolean owner = transfer.getSenderWallet().getUser().getId().equals(requesterId)
                || transfer.getReceiverWallet().getUser().getId().equals(requesterId);
        if (!owner) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You are not allowed to access this transfer");
        }
        return toResponse(transfer);
    }

    private void validate(TransferDto.TransferRequest request) {
        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException("Transfer amount must be positive");
        }
        if (request.receiverUserId() == null) {
            throw new ApiException("Receiver user ID is required");
        }
    }

    private void resetDailyCounterIfNeeded(Wallet wallet) {
        if (wallet.getLastTransferDate() == null || !wallet.getLastTransferDate().isEqual(LocalDate.now())) {
            wallet.setDailyTransferredAmount(BigDecimal.ZERO);
        }
    }

    private void saveEntry(Transfer transfer, Wallet wallet, LedgerEntryType type, BigDecimal amount, BigDecimal balanceAfter) {
        LedgerEntry entry = new LedgerEntry();
        entry.setTransfer(transfer);
        entry.setWallet(wallet);
        entry.setEntryType(type);
        entry.setAmount(amount);
        entry.setBalanceAfter(balanceAfter);
        ledgerEntryRepository.save(entry);
    }

    private void saveAudit(String eventType, UUID userId, String details) {
        AuditEvent event = new AuditEvent();
        event.setEventType(eventType);
        event.setActorUserId(userId);
        event.setDetails(details);
        auditEventRepository.save(event);
    }

    private void recordFailedTransfer(Wallet wallet, BigDecimal amount, UUID counterparty, String reason) {
        WalletTransaction tx = baseTx(wallet, null, WalletOperationType.MONEY_TRANSFERRED,
                WalletOperationStatus.FAILED, amount, counterparty, "N/A");
        tx.setFailureReason(reason);
        walletTransactionRepository.save(tx);
    }

    private void recordSuccess(Wallet wallet, Transfer transfer, WalletOperationType type,
                               BigDecimal amount, UUID counterparty) {
        WalletTransaction tx = baseTx(wallet, transfer, type, WalletOperationStatus.SUCCESS,
                amount, counterparty, transfer.getReference());
        walletTransactionRepository.save(tx);
    }

    private static WalletTransaction baseTx(Wallet wallet, Transfer transfer, WalletOperationType type,
                                            WalletOperationStatus status, BigDecimal amount,
                                            UUID counterparty, String reference) {
        WalletTransaction tx = new WalletTransaction();
        tx.setWallet(wallet);
        tx.setTransfer(transfer);
        tx.setOperationType(type);
        tx.setOperationStatus(status);
        tx.setAmount(amount);
        tx.setCurrency(wallet.getCurrency());
        tx.setCounterpartyUserId(counterparty);
        tx.setReference(reference);
        return tx;
    }

    private TransferDto.TransferResponse toResponse(Transfer transfer) {
        return new TransferDto.TransferResponse(
                transfer.getId(),
                transfer.getReference(),
                transfer.getAmount(),
                transfer.getStatus().name(),
                transfer.getCreatedAt()
        );
    }
}

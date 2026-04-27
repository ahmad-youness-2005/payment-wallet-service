package com.ahmad.wallet.service;

import com.ahmad.wallet.api.dto.WalletDto;
import com.ahmad.wallet.domain.AuditEvent;
import com.ahmad.wallet.domain.Transfer;
import com.ahmad.wallet.domain.UserAccount;
import com.ahmad.wallet.domain.Wallet;
import com.ahmad.wallet.domain.WalletOperationStatus;
import com.ahmad.wallet.domain.WalletOperationType;
import com.ahmad.wallet.domain.WalletStatus;
import com.ahmad.wallet.domain.WalletTransaction;
import com.ahmad.wallet.exception.ApiException;
import com.ahmad.wallet.repository.AuditEventRepository;
import com.ahmad.wallet.repository.TransferRepository;
import com.ahmad.wallet.repository.UserAccountRepository;
import com.ahmad.wallet.repository.WalletRepository;
import com.ahmad.wallet.repository.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WalletService {
    private final UserAccountRepository userRepository;
    private final WalletRepository walletRepository;
    private final TransferRepository transferRepository;
    private final AuditEventRepository auditEventRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    @Value("${app.sandbox.topup-enabled:false}")
    private boolean sandboxTopupEnabled;

    public WalletDto.WalletResponse getCurrentWallet(String email) {
        return toResponse(activeWalletOf(email));
    }

    public WalletDto.TransactionsResponse getCurrentTransactions(String email) {
        Wallet wallet = activeWalletOf(email);
        List<Transfer> transfers = transferRepository
                .findBySenderWalletIdOrReceiverWalletIdOrderByCreatedAtDesc(wallet.getId(), wallet.getId());
        List<WalletDto.TransactionResponse> rows = transfers.stream()
                .map(t -> new WalletDto.TransactionResponse(
                        t.getId(),
                        t.getReference(),
                        t.getAmount(),
                        t.getSenderWallet().getId(),
                        t.getReceiverWallet().getId(),
                        t.getCreatedAt()
                ))
                .toList();
        return new WalletDto.TransactionsResponse(rows);
    }

    public WalletDto.WalletResponse topUp(String email, WalletDto.TopUpRequest request) {
        if (!sandboxTopupEnabled) {
            throw new ApiException("Top-up is disabled outside the local profile");
        }
        UserAccount user = findUser(email);
        Wallet wallet = getActiveWallet(user.getId());
        wallet.setAvailableBalance(wallet.getAvailableBalance().add(request.amount()));
        walletRepository.save(wallet);

        audit("TOPUP", user.getId(), "topup " + request.amount());
        recordTx(wallet, WalletOperationType.MONEY_ADDED, request.amount(), newRef("TOPUP-", 10));

        return toResponse(wallet);
    }

    public WalletDto.WalletHistoryResponse getWalletHistory(String email, WalletDto.WalletHistoryFilter filter) {
        Wallet wallet = activeWalletOf(email);

        int page = filter.page() == null ? 0 : Math.max(filter.page(), 0);
        int size = filter.size() == null ? 20 : Math.min(Math.max(filter.size(), 1), 100);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Specification<WalletTransaction> spec = Specification.where((root, query, cb) ->
                cb.equal(root.get("wallet").get("id"), wallet.getId()));
        if (filter.type() != null && !filter.type().isBlank()) {
            WalletOperationType type = parseFilterEnum(WalletOperationType.class, filter.type(), "type");
            spec = spec.and((root, query, cb) -> cb.equal(root.get("operationType"), type));
        }
        if (filter.status() != null && !filter.status().isBlank()) {
            WalletOperationStatus status = parseFilterEnum(WalletOperationStatus.class, filter.status(), "status");
            spec = spec.and((root, query, cb) -> cb.equal(root.get("operationStatus"), status));
        }
        if (filter.from() != null) {
            Instant from = filter.from().atZone(ZoneId.systemDefault()).toInstant();
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from));
        }
        if (filter.to() != null) {
            Instant to = filter.to().atZone(ZoneId.systemDefault()).toInstant();
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), to));
        }

        Page<WalletTransaction> result = walletTransactionRepository.findAll(spec, pageable);
        List<WalletDto.WalletHistoryItemResponse> items = result.getContent().stream()
                .map(t -> new WalletDto.WalletHistoryItemResponse(
                        t.getId(),
                        t.getOperationType().name(),
                        t.getOperationStatus().name(),
                        t.getAmount(),
                        t.getCurrency(),
                        t.getCounterpartyUserId(),
                        t.getReference(),
                        t.getFailureReason(),
                        t.getCreatedAt()
                ))
                .toList();

        return new WalletDto.WalletHistoryResponse(items, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    public WalletDto.WalletLifecycleResponse freezeActiveWallet(String email) {
        UserAccount user = findUser(email);
        Wallet oldWallet = getActiveWallet(user.getId());
        BigDecimal balance = oldWallet.getAvailableBalance();

        oldWallet.setStatus(WalletStatus.FROZEN);
        oldWallet.setFrozenAt(Instant.now());
        oldWallet.setAvailableBalance(BigDecimal.ZERO);
        walletRepository.save(oldWallet);

        Wallet newWallet = new Wallet();
        newWallet.setUser(user);
        newWallet.setCurrency(oldWallet.getCurrency());
        newWallet.setAvailableBalance(balance);
        walletRepository.save(newWallet);

        String ref = newRef("WALLET-MIG-", 10);
        recordTx(oldWallet, WalletOperationType.WITHDRAWAL, balance, ref);
        recordTx(newWallet, WalletOperationType.MONEY_ADDED, balance, ref);

        audit("WALLET_FROZEN_NEW_OPENED", user.getId(),
                "froze " + oldWallet.getId() + ", opened " + newWallet.getId());

        return new WalletDto.WalletLifecycleResponse(
                oldWallet.getId(),
                oldWallet.getStatus().name(),
                newWallet.getId(),
                balance,
                "Wallet frozen, balance moved to a new wallet"
        );
    }

    @Transactional
    public WalletDto.WalletLifecycleResponse openNewWallet(String email) {
        UserAccount user = findUser(email);
        walletRepository.findFirstByUserIdAndStatusOrderByCreatedAtDesc(user.getId(), WalletStatus.ACTIVE)
                .ifPresent(w -> { throw new ApiException("Active wallet already exists. Freeze it first."); });

        Wallet wallet = new Wallet();
        wallet.setUser(user);
        walletRepository.save(wallet);
        return new WalletDto.WalletLifecycleResponse(
                null,
                null,
                wallet.getId(),
                BigDecimal.ZERO,
                "New wallet opened"
        );
    }

    public WalletDto.WalletListResponse listMyWallets(String email) {
        UserAccount user = findUser(email);
        List<WalletDto.WalletListItemResponse> rows = walletRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(w -> new WalletDto.WalletListItemResponse(
                        w.getId(),
                        w.getAvailableBalance(),
                        w.getCurrency(),
                        w.getStatus().name(),
                        w.getCreatedAt()
                ))
                .toList();
        return new WalletDto.WalletListResponse(rows);
    }

    private UserAccount findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ApiException("User not found"));
    }

    private Wallet activeWalletOf(String email) {
        return getActiveWallet(findUser(email).getId());
    }

    private Wallet getActiveWallet(UUID userId) {
        return walletRepository.findFirstByUserIdAndStatusOrderByCreatedAtDesc(userId, WalletStatus.ACTIVE)
                .orElseThrow(() -> new ApiException("No active wallet found"));
    }

    private WalletDto.WalletResponse toResponse(Wallet wallet) {
        return new WalletDto.WalletResponse(
                wallet.getId(),
                wallet.getAvailableBalance(),
                wallet.getCurrency(),
                wallet.getStatus().name()
        );
    }

    private void recordTx(Wallet wallet, WalletOperationType type, BigDecimal amount, String reference) {
        WalletTransaction tx = new WalletTransaction();
        tx.setWallet(wallet);
        tx.setOperationType(type);
        tx.setOperationStatus(WalletOperationStatus.SUCCESS);
        tx.setAmount(amount);
        tx.setCurrency(wallet.getCurrency());
        tx.setReference(reference);
        walletTransactionRepository.save(tx);
    }

    private void audit(String eventType, UUID userId, String details) {
        AuditEvent event = new AuditEvent();
        event.setEventType(eventType);
        event.setActorUserId(userId);
        event.setDetails(details);
        auditEventRepository.save(event);
    }

    private static String newRef(String prefix, int length) {
        return prefix + UUID.randomUUID().toString().substring(0, length).toUpperCase();
    }

    private static <E extends Enum<E>> E parseFilterEnum(Class<E> enumType, String raw, String label) {
        try {
            return Enum.valueOf(enumType, raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ApiException("Invalid " + label + " filter");
        }
    }
}

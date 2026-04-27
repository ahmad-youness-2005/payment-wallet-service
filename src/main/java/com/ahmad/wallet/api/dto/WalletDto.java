package com.ahmad.wallet.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class WalletDto {
    public record WalletResponse(
            UUID walletId,
            BigDecimal availableBalance,
            String currency,
            String status
    ) {}

    public record TransactionResponse(
            UUID transferId,
            String reference,
            BigDecimal amount,
            UUID senderWalletId,
            UUID receiverWalletId,
            Instant createdAt
    ) {}

    public record TransactionsResponse(
            List<TransactionResponse> transactions
    ) {}

    public record TopUpRequest(
            @NotNull @DecimalMin(value = "0.01") BigDecimal amount
    ) {}

    public record WalletHistoryItemResponse(
            UUID transactionId,
            String type,
            String status,
            BigDecimal amount,
            String currency,
            UUID counterpartyUserId,
            String reference,
            String failureReason,
            Instant createdAt
    ) {}

    public record WalletHistoryResponse(
            List<WalletHistoryItemResponse> items,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {}

    public record WalletHistoryFilter(
            String type,
            String status,
            LocalDateTime from,
            LocalDateTime to,
            Integer page,
            Integer size
    ) {}

    public record WalletLifecycleResponse(
            UUID previousWalletId,
            String previousWalletStatus,
            UUID newWalletId,
            BigDecimal migratedBalance,
            String message
    ) {}

    public record WalletListItemResponse(
            UUID walletId,
            BigDecimal availableBalance,
            String currency,
            String status,
            Instant createdAt
    ) {}

    public record WalletListResponse(
            List<WalletListItemResponse> wallets
    ) {}
}

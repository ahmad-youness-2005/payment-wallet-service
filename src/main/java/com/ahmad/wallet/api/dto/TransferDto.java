package com.ahmad.wallet.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class TransferDto {
    public record TransferRequest(
            @NotNull UUID receiverUserId,
            @NotNull @DecimalMin(value = "0.01") BigDecimal amount
    ) {}

    public record TransferResponse(
            UUID transferId,
            String reference,
            BigDecimal amount,
            String status,
            Instant createdAt
    ) {}
}

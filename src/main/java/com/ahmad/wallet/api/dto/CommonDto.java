package com.ahmad.wallet.api.dto;

import java.time.Instant;

public class CommonDto {
    public record ErrorResponse(
            Instant timestamp,
            int status,
            String error,
            String message
    ) {}
}

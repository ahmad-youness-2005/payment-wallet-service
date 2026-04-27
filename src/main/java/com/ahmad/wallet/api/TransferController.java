package com.ahmad.wallet.api;

import com.ahmad.wallet.api.dto.TransferDto;
import com.ahmad.wallet.service.TransferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@Validated
@RequestMapping("/api/v1/transfers")
@RequiredArgsConstructor
public class TransferController {
    private final TransferService transferService;

    @PostMapping
    public TransferDto.TransferResponse transfer(
            Authentication authentication,
            @RequestBody @Valid TransferDto.TransferRequest request
    ) {
        return transferService.transfer(authentication.getName(), request);
    }

    @GetMapping("/{id}")
    public TransferDto.TransferResponse getTransfer(@PathVariable UUID id, Authentication authentication) {
        return transferService.getTransfer(id, authentication.getName());
    }
}

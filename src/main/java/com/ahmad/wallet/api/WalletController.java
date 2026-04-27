package com.ahmad.wallet.api;

import com.ahmad.wallet.api.dto.WalletDto;
import jakarta.validation.Valid;
import com.ahmad.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/wallet")
@RequiredArgsConstructor
public class WalletController {
    private final WalletService walletService;

    @GetMapping("/me")
    public WalletDto.WalletResponse myWallet(Authentication authentication) {
        return walletService.getCurrentWallet(authentication.getName());
    }

    @GetMapping("/me/transactions")
    public WalletDto.TransactionsResponse myTransactions(Authentication authentication) {
        return walletService.getCurrentTransactions(authentication.getName());
    }

    @GetMapping("/me/history")
    public WalletDto.WalletHistoryResponse myHistory(
            Authentication authentication,
            @ModelAttribute WalletDto.WalletHistoryFilter filter
    ) {
        return walletService.getWalletHistory(authentication.getName(), filter);
    }

    @GetMapping("/me/all")
    public WalletDto.WalletListResponse myWallets(Authentication authentication) {
        return walletService.listMyWallets(authentication.getName());
    }

    @PostMapping("/me/freeze")
    public WalletDto.WalletLifecycleResponse freezeMyWallet(Authentication authentication) {
        return walletService.freezeActiveWallet(authentication.getName());
    }

    @PostMapping("/me/open-new")
    public WalletDto.WalletLifecycleResponse openNewWallet(Authentication authentication) {
        return walletService.openNewWallet(authentication.getName());
    }

    @PostMapping("/me/topup")
    public WalletDto.WalletResponse topUp(Authentication authentication, @RequestBody @Valid WalletDto.TopUpRequest request) {
        return walletService.topUp(authentication.getName(), request);
    }
}

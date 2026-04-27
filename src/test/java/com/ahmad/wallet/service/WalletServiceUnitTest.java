package com.ahmad.wallet.service;

import com.ahmad.wallet.api.dto.WalletDto;
import com.ahmad.wallet.domain.UserAccount;
import com.ahmad.wallet.domain.Wallet;
import com.ahmad.wallet.domain.WalletStatus;
import com.ahmad.wallet.exception.ApiException;
import com.ahmad.wallet.repository.AuditEventRepository;
import com.ahmad.wallet.repository.TransferRepository;
import com.ahmad.wallet.repository.UserAccountRepository;
import com.ahmad.wallet.repository.WalletRepository;
import com.ahmad.wallet.repository.WalletTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletServiceUnitTest {

    @Mock UserAccountRepository userAccountRepository;
    @Mock WalletRepository walletRepository;
    @Mock TransferRepository transferRepository;
    @Mock AuditEventRepository auditEventRepository;
    @Mock WalletTransactionRepository walletTransactionRepository;

    @InjectMocks WalletService walletService;

    private UserAccount user;

    @BeforeEach
    void setUp() {
        user = new UserAccount();
        user.setId(UUID.randomUUID());
        user.setEmail("a@b.com");
    }

    @Test
    void freezeActiveWallet_movesBalanceToANewActiveOne() {
        Wallet old = new Wallet();
        old.setId(UUID.randomUUID());
        old.setUser(user);
        old.setStatus(WalletStatus.ACTIVE);
        old.setCurrency("USD");
        old.setAvailableBalance(new BigDecimal("250.00"));

        when(userAccountRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(walletRepository.findFirstByUserIdAndStatusOrderByCreatedAtDesc(user.getId(), WalletStatus.ACTIVE))
                .thenReturn(Optional.of(old));
        when(walletRepository.save(any(Wallet.class))).thenAnswer(inv -> inv.getArgument(0));

        WalletDto.WalletLifecycleResponse resp = walletService.freezeActiveWallet(user.getEmail());

        ArgumentCaptor<Wallet> saved = ArgumentCaptor.forClass(Wallet.class);
        verify(walletRepository, times(2)).save(saved.capture());

        Wallet frozen = saved.getAllValues().get(0);
        Wallet fresh = saved.getAllValues().get(1);

        assertThat(frozen.getStatus()).isEqualTo(WalletStatus.FROZEN);
        assertThat(frozen.getAvailableBalance()).isEqualByComparingTo("0.00");
        assertThat(fresh.getStatus()).isEqualTo(WalletStatus.ACTIVE);
        assertThat(fresh.getAvailableBalance()).isEqualByComparingTo("250.00");
        assertThat(resp.migratedBalance()).isEqualByComparingTo("250.00");
        assertThat(resp.newWalletId()).isEqualTo(fresh.getId());
    }

    @Test
    void openNewWallet_throws_whenAnActiveWalletAlreadyExists() {
        Wallet active = new Wallet();
        active.setStatus(WalletStatus.ACTIVE);

        when(userAccountRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(walletRepository.findFirstByUserIdAndStatusOrderByCreatedAtDesc(user.getId(), WalletStatus.ACTIVE))
                .thenReturn(Optional.of(active));

        assertThatThrownBy(() -> walletService.openNewWallet(user.getEmail()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Freeze it first");
    }
}

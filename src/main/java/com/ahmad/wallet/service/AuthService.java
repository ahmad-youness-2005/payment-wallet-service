package com.ahmad.wallet.service;

import com.ahmad.wallet.api.dto.AuthDto;
import com.ahmad.wallet.domain.AuditEvent;
import com.ahmad.wallet.domain.UserAccount;
import com.ahmad.wallet.domain.Wallet;
import com.ahmad.wallet.domain.WalletStatus;
import com.ahmad.wallet.exception.ApiException;
import com.ahmad.wallet.repository.AuditEventRepository;
import com.ahmad.wallet.repository.UserAccountRepository;
import com.ahmad.wallet.repository.WalletRepository;
import com.ahmad.wallet.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserAccountRepository userRepository;
    private final WalletRepository walletRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final AuditEventRepository auditEventRepository;

    @Transactional
    public AuthDto.AuthResponse register(AuthDto.RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ApiException("Email already registered");
        }
        UserAccount user = new UserAccount();
        user.setFullName(request.fullName());
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        userRepository.save(user);

        Wallet wallet = new Wallet();
        wallet.setUser(user);
        wallet.setStatus(WalletStatus.ACTIVE);
        walletRepository.save(wallet);

        saveAudit("REGISTER", user.getId(), "user registered + wallet created");
        return new AuthDto.AuthResponse(jwtService.generate(user.getEmail()), user.getEmail(), user.getId());
    }

    public AuthDto.AuthResponse login(AuthDto.LoginRequest request) {
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        UserAccount user = userRepository.findByEmail(request.email()).orElseThrow(() -> new ApiException("User not found"));
        saveAudit("LOGIN", user.getId(), "login ok");
        return new AuthDto.AuthResponse(jwtService.generate(request.email()), request.email(), user.getId());
    }

    private void saveAudit(String eventType, UUID userId, String details) {
        AuditEvent event = new AuditEvent();
        event.setEventType(eventType);
        event.setActorUserId(userId);
        event.setDetails(details);
        auditEventRepository.save(event);
    }
}

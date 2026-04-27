package com.ahmad.wallet.api;

import com.ahmad.wallet.api.dto.AuthDto;
import com.ahmad.wallet.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/register")
    public AuthDto.AuthResponse register(@RequestBody @Valid AuthDto.RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthDto.AuthResponse login(@RequestBody @Valid AuthDto.LoginRequest request) {
        return authService.login(request);
    }
}

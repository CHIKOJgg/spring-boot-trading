package org.example.service;

import lombok.RequiredArgsConstructor;
import org.example.domain.model.entity.*;
import org.example.dto.request.AuthRequest.*;
import org.example.dto.response.ApiResponse.*;
import org.example.exception.GlobalExceptionHandler.*;
import org.example.repository.*;
import org.example.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.*;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TradingAccountRepository tradingAccountRepository;
    private final BankAccountRepository bankAccountRepository;   // Bug fix #7
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    @Value("${jwt.refresh-expiration}")
    private long refreshTokenExpiryMs;

    // -------------------------------------------------------
    //  Login
    // -------------------------------------------------------

    @Transactional
    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));

        UserEntity user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new UserNotFoundException(request.username()));

        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        UserDetails userDetails = userDetailsService.loadUserByUsername(request.username());
        String accessToken  = jwtService.generateAccessToken(userDetails);
        String refreshToken = persistRefreshToken(user, userDetails);

        auditService.logSuccess(user.getUsername(), "LOGIN", "USER", user.getId().toString());
        return AuthResponse.of(accessToken, refreshToken, toUserResponse(user));
    }

    // -------------------------------------------------------
    //  Register
    // -------------------------------------------------------

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.username()))
            throw new DuplicateResourceException("Username already taken: " + request.username());
        if (userRepository.existsByEmail(request.email()))
            throw new DuplicateResourceException("Email already registered: " + request.email());

        RoleEntity traderRole = roleRepository.findByName("ROLE_TRADER")
                .orElseThrow(() -> new TradingException("Role ROLE_TRADER not found"));

        UserEntity user = UserEntity.builder()
                .username(request.username())
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .build();
        user.getRoles().add(traderRole);
        user = userRepository.save(user);

        // Build client profile
        ClientProfileEntity profile = ClientProfileEntity.builder()
                .user(user)
                .firstName(request.firstName())
                .lastName(request.lastName())
                .createdAt(LocalDateTime.now())
                .build();
        user.setProfile(profile);
        user = userRepository.save(user);

        // BUG FIX: new users had no trading account after registration, so the
        // portfolio dashboard was empty and placing orders failed immediately.
        // Automatically provision a default RUB trading account on every signup.
        provisionDefaultAccounts(user);

        UserDetails userDetails = userDetailsService.loadUserByUsername(request.username());
        String accessToken  = jwtService.generateAccessToken(userDetails);
        String refreshToken = persistRefreshToken(user, userDetails);

        auditService.logSuccess(user.getUsername(), "REGISTER", "USER", user.getId().toString());
        return AuthResponse.of(accessToken, refreshToken, toUserResponse(user));
    }

    // -------------------------------------------------------
    //  Refresh token
    // -------------------------------------------------------

    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        RefreshTokenEntity rt = refreshTokenRepository.findByToken(request.refreshToken())
                .orElseThrow(() -> new TradingException("Refresh token not found"));

        if (Boolean.TRUE.equals(rt.getIsRevoked()))
            throw new TradingException("Refresh token revoked");
        if (rt.getExpiresAt().isBefore(LocalDateTime.now()))
            throw new TradingException("Refresh token expired");

        rt.setIsRevoked(true);
        refreshTokenRepository.save(rt);

        UserEntity user = rt.getUser();
        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getUsername());
        String newAccess  = jwtService.generateAccessToken(userDetails);
        String newRefresh = persistRefreshToken(user, userDetails);

        return AuthResponse.of(newAccess, newRefresh, toUserResponse(user));
    }

    // -------------------------------------------------------
    //  Logout / password change
    // -------------------------------------------------------

    @Transactional
    public void logout(String username) {
        userRepository.findByUsername(username).ifPresent(u -> {
            refreshTokenRepository.revokeAllByUserId(u.getId());
            auditService.logSuccess(username, "LOGOUT", "USER", u.getId().toString());
        });
    }

    @Transactional
    public void changePassword(String username, ChangePasswordRequest request) {
        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(username));

        if (!passwordEncoder.matches(request.oldPassword(), user.getPasswordHash()))
            throw new TradingException("Current password is incorrect");

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        refreshTokenRepository.revokeAllByUserId(user.getId());
        auditService.logSuccess(username, "CHANGE_PASSWORD", "USER", user.getId().toString());
    }

    // -------------------------------------------------------
    //  Helpers
    // -------------------------------------------------------

    /**
     * Provision a funded RUB trading account for a newly registered user
     * so they can start trading immediately without extra steps.
     */
    private void provisionDefaultAccounts(UserEntity user) {
        // ── Trading account ─────────────────────────────────────────────────
        if (!tradingAccountRepository.existsByUserId(user.getId())) {
            String tAcc = "T-" + java.util.UUID.randomUUID().toString()
                    .replace("-","").substring(0,10).toUpperCase();
            tradingAccountRepository.save(TradingAccountEntity.builder()
                    .user(user).accountNumber(tAcc).currency("RUB")
                    .cashBalance(BigDecimal.valueOf(1_000_000))
                    .frozenBalance(BigDecimal.ZERO)
                    .status("ACTIVE")
                    .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                    .build());
        }

        // BUG FIX #7: No bank account was created on registration.
        // The "Fund Trading Account" flow requires a bank account as the source,
        // so new users could not deposit or move funds without first manually
        // opening a bank account — the UI for that is not obvious.
        if (!bankAccountRepository.existsByUserId(user.getId())) {
            String bAcc = "B-" + java.util.UUID.randomUUID().toString()
                    .replace("-","").substring(0,10).toUpperCase();
            bankAccountRepository.save(BankAccountEntity.builder()
                    .user(user).accountNumber(bAcc).currency("RUB")
                    .balance(BigDecimal.ZERO)
                    .status("ACTIVE")
                    .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                    .build());
        }
    }

    private String persistRefreshToken(UserEntity user, UserDetails userDetails) {
        String rawToken = jwtService.generateRefreshToken(userDetails);
        long expirySeconds = refreshTokenExpiryMs / 1000;
        RefreshTokenEntity rt = RefreshTokenEntity.builder()
                .user(user)
                .token(rawToken)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusSeconds(expirySeconds))
                .build();
        refreshTokenRepository.save(rt);
        return rawToken;
    }

    private UserResponse toUserResponse(UserEntity u) {
        return new UserResponse(u.getId(), u.getUsername(), u.getEmail(),
                u.getIsActive(), u.getIsLocked(),
                u.getRoles().stream().map(RoleEntity::getName).toList(),
                u.getCreatedAt(), u.getLastLoginAt());
    }
}

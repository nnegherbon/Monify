package com.monify.service;

import com.monify.entity.User;
import com.monify.entity.UserSession;
import com.monify.repository.UserSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Emite e valida os tokens de sessão enviados pelo frontend no cabeçalho
 * "Authorization: Bearer &lt;token&gt;".
 */
@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int SESSION_DAYS = 30;

    private final UserSessionRepository sessionRepository;

    public String issueToken(User user) {
        sessionRepository.deleteExpired(LocalDateTime.now());

        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        sessionRepository.save(UserSession.builder()
                .user(user)
                .tokenHash(hash(token))
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusDays(SESSION_DAYS))
                .build());
        return token;
    }

    @Transactional(readOnly = true)
    public Optional<Long> findUserIdByToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return sessionRepository.findByTokenHash(hash(token))
                .filter(session -> session.getExpiresAt().isAfter(LocalDateTime.now()))
                .map(session -> session.getUser().getId());
    }

    public void revoke(String token) {
        if (token != null && !token.isBlank()) {
            sessionRepository.deleteByTokenHash(hash(token));
        }
    }

    static String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponivel", e);
        }
    }
}

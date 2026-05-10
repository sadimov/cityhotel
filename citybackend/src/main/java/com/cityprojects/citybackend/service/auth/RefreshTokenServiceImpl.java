package com.cityprojects.citybackend.service.auth;

import com.cityprojects.citybackend.entity.core.RefreshToken;
import com.cityprojects.citybackend.exception.AuthenticationException;
import com.cityprojects.citybackend.repository.core.RefreshTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * Implementation rotation + detection de reutilisation des refresh tokens
 * (Tour 38 C6/C7).
 *
 * <p>NE PAS porter {@code @RequireTenant} : ce service est appele depuis le
 * flow d'auth qui n'a pas encore de TenantContext, et depuis le scheduler
 * (mode ROOT). Il scope par {@code userId}, pas par {@code hotelId}.</p>
 */
@Service
@Transactional
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private static final Logger logger = LoggerFactory.getLogger(RefreshTokenServiceImpl.class);

    /** Longueur en octets du token aleatoire (256 bits). Encode Base64 URL-safe ~ 43 chars. */
    private static final int TOKEN_LENGTH_BYTES = 32;

    private final RefreshTokenRepository repository;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Duration refreshDuration;
    /** Transaction REQUIRES_NEW pour la revocation cross-device : doit COMMITER
     *  meme si la transaction appelante (AuthService.refreshToken) rollback en
     *  raison de l'AuthenticationException levee juste apres. */
    private final TransactionTemplate requiresNewTx;

    public RefreshTokenServiceImpl(RefreshTokenRepository repository,
                                   Clock clock,
                                   PlatformTransactionManager txManager,
                                   @Value("${app.jwt.refresh-expiration:604800000}") long refreshExpirationMs) {
        this.repository = repository;
        this.clock = clock;
        this.refreshDuration = Duration.ofMillis(refreshExpirationMs);
        TransactionTemplate tt = new TransactionTemplate(txManager);
        tt.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
        this.requiresNewTx = tt;
    }

    @Override
    public IssuedToken issue(Long userId, Long hotelId, String userAgent, String ipAddress) {
        if (userId == null) {
            throw new IllegalArgumentException("userId required for refresh token issue");
        }
        Instant now = Instant.now(clock);

        byte[] rawBytes = new byte[TOKEN_LENGTH_BYTES];
        secureRandom.nextBytes(rawBytes);
        String clearToken = Base64.getUrlEncoder().withoutPadding().encodeToString(rawBytes);
        String hash = sha256Hex(clearToken);

        RefreshToken entity = new RefreshToken();
        entity.setTokenHash(hash);
        entity.setUserId(userId);
        entity.setHotelId(hotelId);
        entity.setExpiresAt(now.plus(refreshDuration));
        entity.setRevoked(Boolean.FALSE);
        entity.setCreatedAt(now);
        entity.setUserAgent(truncate(userAgent, 200));
        entity.setIpAddress(truncate(ipAddress, 45));

        RefreshToken saved = repository.save(entity);
        logger.debug("Refresh token emis (userId={}, hotelId={}, expiresAt={})",
                userId, hotelId, saved.getExpiresAt());
        return new IssuedToken(clearToken, saved);
    }

    @Override
    public IssuedToken rotate(String oldClearToken, String userAgent, String ipAddress) {
        if (oldClearToken == null || oldClearToken.isBlank()) {
            throw new AuthenticationException("error.auth.refreshToken.missing");
        }
        Instant now = Instant.now(clock);
        String oldHash = sha256Hex(oldClearToken);

        RefreshToken old = repository.findByTokenHash(oldHash)
                .orElseThrow(() -> new AuthenticationException("error.auth.refreshToken.invalid"));

        // Detection de reutilisation : si l'ancien est deja revoked, vol probable.
        // Important : la revocation cross-device est lancee en transaction REQUIRES_NEW
        // afin de COMMITER independamment de l'AuthService transaction qui va rollback
        // en raison de l'AuthenticationException ci-dessous (sans cela, le vol detecte
        // ne se traduirait pas en revocation persistee — finding security critique).
        if (Boolean.TRUE.equals(old.getRevoked())) {
            Long compromisedUserId = old.getUserId();
            Integer revoked = requiresNewTx.execute(status -> repository.revokeByUserId(compromisedUserId));
            logger.warn("Refresh token reutilise (userId={}, oldId={}) — revocation cross-device "
                            + "({} tokens revoques)", compromisedUserId, old.getId(), revoked);
            throw new AuthenticationException("error.auth.refreshToken.reused");
        }

        if (now.isAfter(old.getExpiresAt())) {
            throw new AuthenticationException("error.auth.refreshToken.expired");
        }

        // Emet le nouveau, marque l'ancien revoked + replacedById.
        IssuedToken issued = issue(old.getUserId(), old.getHotelId(), userAgent, ipAddress);
        old.setRevoked(Boolean.TRUE);
        old.setReplacedById(issued.entity().getId());
        repository.save(old);

        logger.debug("Refresh token rotate (userId={}, oldId={}, newId={})",
                old.getUserId(), old.getId(), issued.entity().getId());
        return issued;
    }

    @Override
    public int revokeAllForUser(Long userId) {
        if (userId == null) {
            return 0;
        }
        int count = repository.revokeByUserId(userId);
        if (count > 0) {
            logger.info("Refresh tokens revoques pour userId={} (count={})", userId, count);
        }
        return count;
    }

    @Override
    public int purgeExpired() {
        Instant threshold = Instant.now(clock);
        int deleted = repository.deleteExpiredOlderThan(threshold);
        if (deleted > 0) {
            logger.info("Purge refresh tokens : {} expires supprimes", deleted);
        }
        return deleted;
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    sb.append('0');
                }
                sb.append(hex);
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 est garanti par toutes les JVM standard. Si absent, panique.
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() > max ? value.substring(0, max) : value;
    }
}

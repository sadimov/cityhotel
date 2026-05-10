package com.cityprojects.citybackend.service.auth;

import com.cityprojects.citybackend.entity.core.RefreshToken;
import com.cityprojects.citybackend.exception.AuthenticationException;
import com.cityprojects.citybackend.repository.core.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests Surefire de {@link RefreshTokenServiceImpl} (Tour 38 C6/C7).
 *
 * <p>Couvre :
 * <ul>
 *   <li>{@code issue} : token clair retourne, hash persiste, expiration calculee.</li>
 *   <li>{@code rotate} : ancien revoked, nouveau emis, lien replacedById.</li>
 *   <li>{@code rotate} : detection de reutilisation -> revocation cross-device + 401.</li>
 *   <li>{@code rotate} : token invalide / expire -> AuthenticationException.</li>
 *   <li>{@code revokeAllForUser} : delegation au repository.</li>
 *   <li>{@code purgeExpired} : delegation au repository.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTests {

    private static final Long USER_ID = 42L;
    private static final Long HOTEL_ID = 7L;
    private static final long REFRESH_EXP_MS = 604_800_000L; // 7 jours

    @Mock
    private RefreshTokenRepository repository;

    @Mock
    private PlatformTransactionManager txManager;

    private RefreshTokenServiceImpl service;
    private Clock fixedClock;
    private Instant now;

    @BeforeEach
    void setUp() {
        now = Instant.parse("2026-05-09T10:00:00Z");
        fixedClock = Clock.fixed(now, ZoneId.of("UTC"));
        // Le PlatformTransactionManager mock retourne un TransactionStatus benin,
        // permettant a TransactionTemplate.execute() de simuler la transaction
        // sans driver JDBC reel.
        org.mockito.Mockito.lenient()
                .when(txManager.getTransaction(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new SimpleTransactionStatus());
        service = new RefreshTokenServiceImpl(repository, fixedClock, txManager, REFRESH_EXP_MS);
    }

    @Test
    @DisplayName("issue() retourne un token clair non vide et persiste l'entite avec hash + expiration")
    void issue_returnsClearTokenAndPersistsEntity() {
        when(repository.save(any(RefreshToken.class))).thenAnswer(invocation -> {
            RefreshToken rt = invocation.getArgument(0);
            rt.setId(123L);
            return rt;
        });

        RefreshTokenService.IssuedToken issued = service.issue(USER_ID, HOTEL_ID, "JUnit/1.0", "127.0.0.1");

        assertNotNull(issued.clearToken(), "clearToken must not be null");
        assertFalse(issued.clearToken().isBlank(), "clearToken must not be blank");
        assertNotNull(issued.entity(), "entity must not be null");

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository, times(1)).save(captor.capture());
        RefreshToken saved = captor.getValue();
        assertEquals(USER_ID, saved.getUserId());
        assertEquals(HOTEL_ID, saved.getHotelId());
        assertNotNull(saved.getTokenHash(), "hash must be populated");
        // Le hash doit etre different du token clair.
        assertNotEquals(issued.clearToken(), saved.getTokenHash(), "hash != clearToken");
        // Expiration = now + 7j
        assertEquals(now.plusMillis(REFRESH_EXP_MS), saved.getExpiresAt());
        assertFalse(Boolean.TRUE.equals(saved.getRevoked()), "revoked must default to false");
    }

    @Test
    @DisplayName("rotate() avec token valide : ancien revoked + lien replacedById, nouveau emis")
    void rotate_validToken_revokesOldAndIssuesNew() {
        // 1. Issue un premier token et capture le hash sauvegarde
        when(repository.save(any(RefreshToken.class))).thenAnswer(invocation -> {
            RefreshToken rt = invocation.getArgument(0);
            if (rt.getId() == null) {
                rt.setId(rt.getTokenHash().hashCode() < 0
                        ? -(long) rt.getTokenHash().hashCode()
                        : (long) rt.getTokenHash().hashCode());
            }
            return rt;
        });

        RefreshTokenService.IssuedToken first = service.issue(USER_ID, HOTEL_ID, null, null);
        Long firstId = first.entity().getId();

        // 2. Quand on rotate, le repository doit retrouver le token par hash.
        when(repository.findByTokenHash(first.entity().getTokenHash()))
                .thenReturn(Optional.of(first.entity()));

        RefreshTokenService.IssuedToken rotated = service.rotate(first.clearToken(), "JUnit/2.0", "10.0.0.1");

        assertNotNull(rotated, "rotated must not be null");
        assertNotEquals(first.clearToken(), rotated.clearToken(), "new clearToken must differ");
        // L'ancien doit etre revoked + replacedById set
        assertTrue(Boolean.TRUE.equals(first.entity().getRevoked()), "old token must be revoked");
        assertEquals(rotated.entity().getId(), first.entity().getReplacedById(),
                "replacedById must point to the new token");
    }

    @Test
    @DisplayName("rotate() detecte la reutilisation : revoque tous les tokens du user + leve AuthenticationException")
    void rotate_reusedRevokedToken_revokesAllAndThrows() {
        // Construit un token deja revoked
        RefreshToken oldRevoked = new RefreshToken();
        oldRevoked.setId(99L);
        oldRevoked.setUserId(USER_ID);
        oldRevoked.setHotelId(HOTEL_ID);
        oldRevoked.setRevoked(Boolean.TRUE);
        oldRevoked.setExpiresAt(now.plusSeconds(3600));
        oldRevoked.setTokenHash("dummy-hash-not-derivable");

        // findByTokenHash retournera oldRevoked pour n'importe quel hash demande.
        when(repository.findByTokenHash(anyString())).thenReturn(Optional.of(oldRevoked));
        when(repository.revokeByUserId(USER_ID)).thenReturn(3);

        AuthenticationException ex = assertThrows(AuthenticationException.class,
                () -> service.rotate("any-clear-token", null, null));

        assertEquals("error.auth.refreshToken.reused", ex.getMessage());
        verify(repository, times(1)).revokeByUserId(USER_ID);
    }

    @Test
    @DisplayName("rotate() avec token expire : leve AuthenticationException error.auth.refreshToken.expired")
    void rotate_expiredToken_throws() {
        RefreshToken expired = new RefreshToken();
        expired.setId(50L);
        expired.setUserId(USER_ID);
        expired.setRevoked(Boolean.FALSE);
        expired.setExpiresAt(now.minusSeconds(1)); // deja expire
        expired.setTokenHash("hash");

        when(repository.findByTokenHash(anyString())).thenReturn(Optional.of(expired));

        AuthenticationException ex = assertThrows(AuthenticationException.class,
                () -> service.rotate("clear", null, null));
        assertEquals("error.auth.refreshToken.expired", ex.getMessage());
    }

    @Test
    @DisplayName("rotate() avec token introuvable : leve AuthenticationException error.auth.refreshToken.invalid")
    void rotate_unknownToken_throws() {
        when(repository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        AuthenticationException ex = assertThrows(AuthenticationException.class,
                () -> service.rotate("unknown-clear", null, null));
        assertEquals("error.auth.refreshToken.invalid", ex.getMessage());
    }

    @Test
    @DisplayName("rotate() sans token : leve AuthenticationException error.auth.refreshToken.missing")
    void rotate_blankToken_throws() {
        AuthenticationException ex = assertThrows(AuthenticationException.class,
                () -> service.rotate("   ", null, null));
        assertEquals("error.auth.refreshToken.missing", ex.getMessage());
    }

    @Test
    @DisplayName("revokeAllForUser() delegue au repository")
    void revokeAllForUser_delegates() {
        when(repository.revokeByUserId(USER_ID)).thenReturn(2);
        int count = service.revokeAllForUser(USER_ID);
        assertEquals(2, count);
        verify(repository, times(1)).revokeByUserId(USER_ID);
    }

    @Test
    @DisplayName("purgeExpired() delegue au repository avec now() comme threshold")
    void purgeExpired_delegates() {
        when(repository.deleteExpiredOlderThan(eq(now))).thenReturn(7);
        int count = service.purgeExpired();
        assertEquals(7, count);
        verify(repository, times(1)).deleteExpiredOlderThan(now);
    }
}

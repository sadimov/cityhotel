package com.cityprojects.citybackend.repository.core;

import com.cityprojects.citybackend.entity.core.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository pour {@link RefreshToken} (Tour 38 C6/C7).
 *
 * <p>Pas de filtrage hotelId : entite technique scopee par userId.</p>
 */
@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /**
     * Recherche par hash SHA-256 du token clair (single source of truth).
     */
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Tous les refresh tokens encore actifs d'un utilisateur (utile pour
     * detection de session multi-device et stats).
     */
    List<RefreshToken> findByUserIdAndRevokedFalse(Long userId);

    /**
     * Revoque tous les refresh tokens d'un utilisateur (logout cross-device,
     * detection de vol). Modifying batch update — pas de chargement individuel.
     */
    @Modifying
    @Transactional
    @Query("UPDATE RefreshToken rt SET rt.revoked = true WHERE rt.userId = :userId AND rt.revoked = false")
    int revokeByUserId(@Param("userId") Long userId);

    /**
     * Purge les tokens dont l'expiration est plus ancienne que {@code threshold}.
     * Appele par le scheduler de purge nocturne. Retourne le nombre de lignes
     * effectivement supprimees (telemetric).
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiresAt < :threshold")
    int deleteExpiredOlderThan(@Param("threshold") Instant threshold);
}

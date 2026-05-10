package com.cityprojects.citybackend.entity.core;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Refresh token persiste pour la rotation + revocation (Tour 38 C6/C7).
 *
 * <p>Entite TECHNIQUE non tenant-aware : le scoping se fait par {@code userId}
 * (et accessoirement {@code hotelId} pour audit). Pas de {@code @TenantId}
 * volontaire — le SUPERADMIN ROOT a {@code hotelId=null}, ce qu'un discriminator
 * tenant rejetterait au INSERT.</p>
 *
 * <h3>Securite</h3>
 * <ul>
 *   <li>{@code tokenHash} = SHA-256 du token clair (Base64 URL-safe). Le token
 *       clair n'est jamais stocke et n'est emis qu'une seule fois au client.</li>
 *   <li>{@code revoked + replacedById} permettent la detection de reutilisation :
 *       si un client renvoie un refresh deja revoked, on revoque TOUS les refresh
 *       de ce user (vol probable).</li>
 *   <li>{@code expiresAt} = 7j par defaut (cf. {@code app.jwt.refresh-expiration}).</li>
 * </ul>
 *
 * <h3>Pas d'AuditableEntity</h3>
 * <p>Le created_by/updated_by est inutile ici : le proprietaire du token est
 * deja {@code userId}. {@code createdAt} est porte directement par cette entite.</p>
 */
@Entity
@Table(name = "refresh_tokens", schema = "core")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "token_hash", nullable = false, unique = true, length = 128, updatable = false)
    private String tokenHash;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    /** Nullable : un SUPERADMIN ROOT peut emettre un refresh sans tenant. */
    @Column(name = "hotel_id", nullable = true, updatable = false)
    private Long hotelId;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "revoked", nullable = false)
    private Boolean revoked = Boolean.FALSE;

    /** ID du nouveau RefreshToken qui a remplace celui-ci lors d'un rotate. */
    @Column(name = "replaced_by_id", nullable = true)
    private Long replacedById;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "user_agent", length = 200, updatable = false)
    private String userAgent;

    @Column(name = "ip_address", length = 45, updatable = false)
    private String ipAddress;

    public RefreshToken() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getHotelId() {
        return hotelId;
    }

    public void setHotelId(Long hotelId) {
        this.hotelId = hotelId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Boolean getRevoked() {
        return revoked;
    }

    public void setRevoked(Boolean revoked) {
        this.revoked = revoked;
    }

    public Long getReplacedById() {
        return replacedById;
    }

    public void setReplacedById(Long replacedById) {
        this.replacedById = replacedById;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    /**
     * Verifie si le token est encore exploitable (non revoque + non expire).
     */
    public boolean isUsable(Instant now) {
        return !Boolean.TRUE.equals(revoked) && expiresAt != null && now.isBefore(expiresAt);
    }
}

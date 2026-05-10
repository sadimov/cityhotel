package com.cityprojects.citybackend.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Utilitaire pour la gestion des tokens JWT.
 * Compatible JJWT 0.12.x.
 *
 * <p>Tour 38 H4 : durcissement charset (UTF-8 explicite) + validation
 * post-construction (longueur >= 64, refus du secret hardcoded historique).</p>
 */
@Component
public class JwtTokenProvider {

    private static final Logger logger = LoggerFactory.getLogger(JwtTokenProvider.class);

    /** Longueur minimale du secret JWT en caracteres (HS256 exige >= 256 bits = 32 octets ASCII).
     *  On pousse a 64 pour reserver une marge sur HS512 et resister a la cryptanalyse. */
    private static final int MIN_SECRET_LENGTH = 64;

    /** Prefixe du secret hardcoded historique dans application.yml pre-Tour 38. Refuse au boot. */
    private static final String LEGACY_DEFAULT_PREFIX = "mySecretKey";

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.expiration}")
    private int jwtExpirationInMs;

    /**
     * Tour 38 H4 : validation des le boot que le secret JWT est conforme.
     * Empeche un demarrage en prod avec un secret faible / hardcoded.
     */
    @PostConstruct
    void validate() {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalStateException(
                    "JWT secret is null or blank — set JWT_SECRET environment variable.");
        }
        if (jwtSecret.startsWith(LEGACY_DEFAULT_PREFIX)) {
            throw new IllegalStateException(
                    "JWT secret uses the legacy hardcoded default — refuse to start. "
                  + "Generate a cryptographically random 64+ character secret and set JWT_SECRET.");
        }
        if (jwtSecret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(
                    "JWT secret is too short (" + jwtSecret.length() + " chars) — minimum "
                  + MIN_SECRET_LENGTH + " required. Generate a stronger secret.");
        }
        logger.info("JWT secret validated (length={} chars)", jwtSecret.length());
    }

    /**
     * Genere la cle de signature securisee.
     * Tour 38 H4 : charset UTF-8 explicite (sinon dependance a la locale JVM).
     */
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Génère un token JWT à partir de l'authentification
     */
    public String generateToken(Authentication authentication) {
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();

        Date expiryDate = new Date(System.currentTimeMillis() + jwtExpirationInMs);

        return Jwts.builder()
                .subject(Long.toString(userPrincipal.getUserId()))
                .claim("username", userPrincipal.getUsername())
                .claim("email", userPrincipal.getEmail())
                .claim("hotelId", userPrincipal.getHotelId())
                .claim("hotelCode", userPrincipal.getHotelCode())
                .claim("roleCode", userPrincipal.getRoleCode())
                .issuedAt(new Date())
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Génère un token JWT pour un utilisateur spécifique
     */
    public String generateTokenForUser(UserPrincipal userPrincipal) {
        Date expiryDate = new Date(System.currentTimeMillis() + jwtExpirationInMs);

        return Jwts.builder()
                .subject(Long.toString(userPrincipal.getUserId()))
                .claim("username", userPrincipal.getUsername())
                .claim("email", userPrincipal.getEmail())
                .claim("hotelId", userPrincipal.getHotelId())
                .claim("hotelCode", userPrincipal.getHotelCode())
                .claim("roleCode", userPrincipal.getRoleCode())
                .issuedAt(new Date())
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Extrait l'ID utilisateur du token JWT
     */
    public Long getUserIdFromJWT(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return Long.parseLong(claims.getSubject());
    }

    /**
     * Extrait le nom d'utilisateur du token JWT
     */
    public String getUsernameFromJWT(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return claims.get("username", String.class);
    }

    /**
     * Extrait l'ID de l'hôtel du token JWT
     */
    public Long getHotelIdFromJWT(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return claims.get("hotelId", Long.class);
    }

    /**
     * Extrait le code rôle du token JWT
     */
    public String getRoleCodeFromJWT(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return claims.get("roleCode", String.class);
    }

    /**
     * Valide un token JWT
     */
    public boolean validateToken(String authToken) {
        try {
            Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(authToken);
            return true;
        } catch (SecurityException ex) {
            logger.error("Signature JWT invalide: {}", ex.getMessage());
        } catch (MalformedJwtException ex) {
            logger.error("Token JWT invalide: {}", ex.getMessage());
        } catch (ExpiredJwtException ex) {
            logger.error("Token JWT expiré: {}", ex.getMessage());
        } catch (UnsupportedJwtException ex) {
            logger.error("Token JWT non supporté: {}", ex.getMessage());
        } catch (IllegalArgumentException ex) {
            logger.error("JWT claims string vide: {}", ex.getMessage());
        } catch (Exception ex) {
            logger.error("Erreur de validation JWT: {}", ex.getMessage());
        }
        return false;
    }

    /**
     * Obtient la date d'expiration du token
     */
    public Date getExpirationDateFromJWT(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return claims.getExpiration();
    }

    /**
     * Vérifie si le token est expiré
     */
    public boolean isTokenExpired(String token) {
        try {
            Date expiration = getExpirationDateFromJWT(token);
            return expiration.before(new Date());
        } catch (Exception e) {
            logger.error("Erreur lors de la vérification de l'expiration: {}", e.getMessage());
            return true; // Considérer comme expiré en cas d'erreur
        }
    }

    /**
     * Extrait toutes les claims du token
     */
    public Claims getAllClaimsFromToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
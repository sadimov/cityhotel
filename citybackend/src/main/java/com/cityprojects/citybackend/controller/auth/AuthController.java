package com.cityprojects.citybackend.controller.auth;

import com.cityprojects.citybackend.dto.auth.LoginRequest;
import com.cityprojects.citybackend.dto.auth.LoginResponse;
import com.cityprojects.citybackend.dto.auth.RefreshTokenRequest;
import com.cityprojects.citybackend.service.auth.AuthService;
import com.cityprojects.citybackend.util.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Controleur pour l'authentification.
 * <p>
 * <b>CORS</b> : NE PAS ajouter {@code @CrossOrigin} ici. La politique CORS est
 * gere centralement par {@code SecurityConfig.corsConfigurationSource()} (whitelist
 * via {@code app.cors.allowed-origins}). Une annotation locale {@code origins="*"}
 * ouvrirait l'API a tous les domaines en bypassant le SecurityFilterChain
 * — supprimee au Tour 7B I5.
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    @Autowired
    private AuthService authService;

    /**
     * Connexion utilisateur — endpoint PUBLIC (whitelist /auth/login dans SecurityConfig).
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest loginRequest,
            HttpServletRequest request) {

        logger.info("Tentative de connexion pour l'utilisateur : {}", loginRequest.getUsername());

        try {
            String clientIp = getClientIpAddress(request);
            String userAgent = request.getHeader("User-Agent");

            LoginResponse response = authService.login(loginRequest, clientIp, userAgent);

            logger.info("Connexion réussie pour l'utilisateur : {} (Hotel: {})",
                       loginRequest.getUsername(), response.getHotelCode());

            return ResponseEntity.ok(ApiResponse.success(response, "Connexion réussie"));

        } catch (Exception e) {
            logger.error("Erreur lors de la connexion pour l'utilisateur : {}", loginRequest.getUsername(), e);
            return ResponseEntity.badRequest()
                .body(ApiResponse.error("Erreur de connexion : " + e.getMessage()));
        }
    }

    /**
     * Deconnexion utilisateur — exige authentification (un anonyme n'a rien a deconnecter).
     */
    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest request) {
        try {
            String token = getJwtFromRequest(request);
            if (token != null) {
                authService.logout(token);
            }

            logger.info("Déconnexion réussie");
            return ResponseEntity.ok(ApiResponse.success(null, "Déconnexion réussie"));

        } catch (Exception e) {
            logger.error("Erreur lors de la déconnexion", e);
            return ResponseEntity.badRequest()
                .body(ApiResponse.error("Erreur de déconnexion : " + e.getMessage()));
        }
    }

    /**
     * Rafraichissement du token — endpoint PUBLIC (le client n'a peut-etre plus
     * de session valide). La validite du refresh token est verifiee dans le service.
     */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<LoginResponse>> refreshToken(
            @Valid @RequestBody RefreshTokenRequest refreshRequest) {

        try {
            LoginResponse response = authService.refreshToken(refreshRequest.getToken());

            logger.info("Token rafraîchi avec succès");
            return ResponseEntity.ok(ApiResponse.success(response, "Token rafraîchi avec succès"));

        } catch (Exception e) {
            logger.error("Erreur lors du rafraîchissement du token", e);
            return ResponseEntity.badRequest()
                .body(ApiResponse.error("Erreur de rafraîchissement : " + e.getMessage()));
        }
    }

    /**
     * Validation du token — exige authentification : un anonyme n'a aucune raison
     * de demander si un JWT est valide (et exposer cette capacite ouvre un canal
     * d'oracle pour brute-forcer des tokens).
     */
    @PostMapping("/validate")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Boolean>> validateToken(
            @RequestParam String token) {

        try {
            boolean isValid = authService.validateToken(token);

            return ResponseEntity.ok(ApiResponse.success(isValid,
                isValid ? "Token valide" : "Token invalide"));

        } catch (Exception e) {
            logger.error("Erreur lors de la validation du token", e);
            return ResponseEntity.badRequest()
                .body(ApiResponse.error("Erreur de validation : " + e.getMessage()));
        }
    }

    /**
     * Obtenir les informations de l'utilisateur connecte — exige authentification.
     */
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<LoginResponse>> getCurrentUser(HttpServletRequest request) {
        try {
            String token = getJwtFromRequest(request);
            if (token == null) {
                return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Token manquant"));
            }

            LoginResponse response = authService.getUserInfo(token);

            return ResponseEntity.ok(ApiResponse.success(response, "Informations utilisateur récupérées"));

        } catch (Exception e) {
            logger.error("Erreur lors de la récupération des informations utilisateur", e);
            return ResponseEntity.badRequest()
                .body(ApiResponse.error("Erreur : " + e.getMessage()));
        }
    }

    /**
     * Statistiques des sessions actives — reserve aux SUPERADMIN (vue cross-tenant
     * des sessions globales, ne doit pas fuiter aux admins d'hotels).
     */
    @GetMapping("/sessions/stats")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<ApiResponse<Object>> getSessionStats() {
        try {
            Object stats = authService.getSessionStats();

            return ResponseEntity.ok(ApiResponse.success(stats, "Statistiques des sessions récupérées"));

        } catch (Exception e) {
            logger.error("Erreur lors de la récupération des statistiques de sessions", e);
            return ResponseEntity.badRequest()
                .body(ApiResponse.error("Erreur : " + e.getMessage()));
        }
    }

    /**
     * Extraction de l'adresse IP du client
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedForHeader = request.getHeader("X-Forwarded-For");
        if (xForwardedForHeader == null || xForwardedForHeader.isEmpty()) {
            return request.getRemoteAddr();
        } else {
            // X-Forwarded-For peut contenir plusieurs IP séparées par des virgules
            return xForwardedForHeader.split(",")[0].trim();
        }
    }

    /**
     * Extraction du token JWT de la requête
     */
    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}

package com.cityprojects.citybackend.service.auth;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.dto.auth.LoginRequest;
import com.cityprojects.citybackend.dto.auth.LoginResponse;
import com.cityprojects.citybackend.entity.core.DBUser;
import com.cityprojects.citybackend.entity.core.UserSession;
import com.cityprojects.citybackend.exception.AuthenticationException;
import com.cityprojects.citybackend.repository.core.DBUserRepository;
import com.cityprojects.citybackend.repository.core.UserSessionRepository;
import com.cityprojects.citybackend.security.JwtTokenProvider;
import com.cityprojects.citybackend.security.UserPrincipal;
import com.cityprojects.citybackend.util.PasswordUtil;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Service d'authentification
 * Version mise à jour sans gestion du salt séparé
 */
@Service
@Transactional
public class AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);
    private static final int MAX_LOGIN_ATTEMPTS = 5;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private DBUserRepository userRepository;

    @Autowired
    private UserSessionRepository sessionRepository;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // Tour 38 C6/C7 : refresh token rotation + revocation.
    @Autowired
    private RefreshTokenService refreshTokenService;

    @Value("${app.security.session.max-sessions-per-user:3}")
    private int maxSessionsPerUser;

    @Value("${app.jwt.expiration:3600000}")
    private long jwtExpirationMs;

    /**
     * Authentification d'un utilisateur
     */
    public LoginResponse login(LoginRequest loginRequest, String clientIp, String userAgent) {
        
        logger.info("🔐 Tentative de connexion pour : {}", loginRequest.getUsername());
        
        // Vérifier si l'utilisateur existe
        DBUser user = userRepository.findByUsernameAndActifTrue(loginRequest.getUsername())
                .orElseThrow(() -> new AuthenticationException("Nom d'utilisateur ou mot de passe incorrect"));

        // Vérifier si le compte n'est pas verrouillé
        if (user.getCompteVerrouille()) {
            logger.warn("🔒 Tentative de connexion sur compte verrouillé : {}", loginRequest.getUsername());
            throw new AuthenticationException("Compte verrouillé. Contactez l'administrateur.");
        }

        // Vérifier le mot de passe avec BCrypt directement
        if (!passwordEncoder.matches(loginRequest.getPassword(), user.getPasswordHash())) {
            handleFailedLogin(user);
            logger.warn("❌ Mot de passe incorrect pour : {}", loginRequest.getUsername());
            throw new AuthenticationException("Nom d'utilisateur ou mot de passe incorrect");
        }

        logger.info("✅ Mot de passe vérifié pour : {}", loginRequest.getUsername());

        // Authentifier avec Spring Security
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginRequest.getUsername(),
                            loginRequest.getPassword()
                    )
            );
        } catch (Exception e) {
            logger.error("❌ Erreur lors de l'authentification Spring Security pour : {}", 
                        loginRequest.getUsername(), e);
            throw new AuthenticationException("Erreur d'authentification : " + e.getMessage());
        }

        SecurityContextHolder.getContext().setAuthentication(authentication);
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();

        // Générer le token JWT
        String jwt = tokenProvider.generateToken(authentication);
        logger.info("🎫 Token JWT généré pour : {}", loginRequest.getUsername());

        // Gérer les sessions concurrentes
        manageConcurrentSessions(user.getUserId());

        // Créer une nouvelle session
        String sessionId = createUserSession(user, clientIp, userAgent);

        // Mettre à jour la dernière connexion et réinitialiser les tentatives
        userRepository.updateDerniereConnexion(user.getUserId(), LocalDateTime.now());

        // Tour 38 C6/C7 : emission d'un refresh token persiste (rotation + revocation).
        // hotelId nullable pour SUPERADMIN ROOT (mais ici on a toujours user.getHotel()
        // — meme le superadmin systeme est rattache a l'hotel SYSTEM via 014).
        Long refreshHotelId = user.getHotel() != null ? user.getHotel().getHotelId() : null;
        RefreshTokenService.IssuedToken refreshIssued =
                refreshTokenService.issue(user.getUserId(), refreshHotelId, userAgent, clientIp);

        // Construire la réponse
        LoginResponse response = buildLoginResponse(jwt, userPrincipal, sessionId);
        response.setRefreshToken(refreshIssued.clearToken());

        logger.info("🎉 Connexion réussie pour l'utilisateur {} (Hotel: {})", 
                   user.getUsername(), user.getHotel().getHotelCode());

        return response;
    }

    /**
     * Deconnexion d'un utilisateur (Tour 38 C6/C7 : revoque aussi les refresh tokens).
     */
    public void logout(String token) {
        try {
            if (tokenProvider.validateToken(token)) {
                Long userId = tokenProvider.getUserIdFromJWT(token);

                // Desactiver toutes les sessions de l'utilisateur
                sessionRepository.deactivateUserSessions(userId);

                // Tour 38 C6/C7 : revoque tous les refresh tokens cross-device.
                // Sans cela, un attaquant ayant un refresh token vole pourrait
                // reprendre une session apres logout.
                refreshTokenService.revokeAllForUser(userId);

                // Effacer le contexte de securite
                SecurityContextHolder.clearContext();

                logger.info("Deconnexion reussie pour l'utilisateur ID: {}", userId);
            }
        } catch (RuntimeException e) {
            // Garde non-silencieuse : on log mais on ne propage pas pour ne pas
            // bloquer le client qui veut juste invalider sa session locale.
            logger.warn("Erreur lors de la deconnexion (token possiblement deja expire)", e);
        }
    }

    /**
     * Rafraichissement du token via rotation du refresh token (Tour 38 C6/C7).
     *
     * <p>Le parametre {@code refreshTokenStr} est un REFRESH TOKEN (chaine
     * Base64 URL-safe persistee), pas un access JWT. La rotation :
     * <ol>
     *   <li>valide / detecte la reutilisation,</li>
     *   <li>marque l'ancien revoked,</li>
     *   <li>emet un nouveau refresh,</li>
     *   <li>genere un nouvel access JWT.</li>
     * </ol>
     */
    public LoginResponse refreshToken(String refreshTokenStr) {
        // Etape 1 : rotation (peut lever AuthenticationException pour invalid/expired/reused).
        RefreshTokenService.IssuedToken rotated = refreshTokenService.rotate(refreshTokenStr, null, null);

        // Etape 2 : recharger l'utilisateur (peut avoir ete verrouille / desactive entre-temps).
        Long userId = rotated.entity().getUserId();
        DBUser user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthenticationException("error.auth.user.notFound"));

        if (!user.getActif() || user.getCompteVerrouille()) {
            // Securite : on revoque tous les refresh de ce user pour qu'il ne puisse
            // pas en re-utiliser un autre depuis un autre device.
            refreshTokenService.revokeAllForUser(userId);
            throw new AuthenticationException("error.auth.account.disabled");
        }

        // Etape 3 : nouveau access JWT (court, 1h).
        UserPrincipal userPrincipal = UserPrincipal.create(user,
                java.util.Collections.singletonList(
                        new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + user.getRole().getRoleCode())
                ));
        String newJwt = tokenProvider.generateTokenForUser(userPrincipal);

        // Etape 4 : reponse complete (nouveau refresh + nouveau access).
        LoginResponse response = buildLoginResponse(newJwt, userPrincipal, null);
        response.setRefreshToken(rotated.clearToken());
        return response;
    }

    /**
     * Validation d'un token
     */
    public boolean validateToken(String token) {
        return tokenProvider.validateToken(token);
    }

    /**
     * Obtenir les informations utilisateur à partir du token
     */
    public LoginResponse getUserInfo(String token) {
        if (!tokenProvider.validateToken(token)) {
            throw new AuthenticationException("Token invalide ou expiré");
        }

        Long userId = tokenProvider.getUserIdFromJWT(token);
        DBUser user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthenticationException("Utilisateur non trouvé"));

        UserPrincipal userPrincipal = UserPrincipal.create(user, 
                java.util.Collections.singletonList(
                        new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + user.getRole().getRoleCode())
                ));

        LoginResponse response = new LoginResponse();
        response.setUserId(user.getUserId());
        response.setUsername(user.getUsername());
        response.setEmail(user.getEmail());
        response.setPrenom(user.getPrenom());
        response.setNom(user.getNom());
        // Tour 7C : hotelId retiré du DTO ; le front lit le claim JWT "hotelId".
        response.setHotelCode(user.getHotel().getHotelCode());
        response.setHotelNom(user.getHotel().getHotelNom());
        response.setRoleCode(user.getRole().getRoleCode());
        response.setRoleNom(user.getRole().getRoleNom());
        response.setDerniereConnexion(user.getDerniereConnexion());

        return response;
    }

    /**
     * Obtenir les statistiques des sessions
     */
    public Map<String, Object> getSessionStats() {
        Map<String, Object> stats = new HashMap<>();
        
        stats.put("totalActiveSessions", sessionRepository.countByActifTrue());
        stats.put("maxSessionsPerUser", maxSessionsPerUser);
        stats.put("maxTotalSessions", 80); // Configuration pour 80 sessions simultanées
        
        // Nettoyer les sessions expirées
        int expiredSessions = sessionRepository.deactivateExpiredSessions(
                LocalDateTime.now().minusMinutes(30));
        stats.put("expiredSessionsCleared", expiredSessions);
        
        return stats;
    }

    /**
     * Création d'un utilisateur avec mot de passe hashé
     */
    @Transactional
    public DBUser createUser(String username, String email, String password, 
                           String prenom, String nom, Long hotelId, Integer roleId) {
        
        // Valider le mot de passe
        PasswordUtil.PasswordValidationResult validation = PasswordUtil.validatePassword(password);
        if (!validation.isValid()) {
            throw new IllegalArgumentException("Mot de passe invalide : " + validation.getFirstError());
        }
        
        // Vérifier l'unicité
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Ce nom d'utilisateur existe déjà");
        }
        
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Cette adresse email existe déjà");
        }
        
        // Hasher le mot de passe
        String hashedPassword = passwordEncoder.encode(password);
        
        // Créer l'utilisateur
        DBUser user = new DBUser();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(hashedPassword);
        user.setPrenom(prenom);
        user.setNom(nom);
        // Note: hotel et role doivent être récupérés depuis leurs repositories respectifs
        
        return userRepository.save(user);
    }

    /**
     * Mise à jour du mot de passe
     */
    @Transactional
    public void updatePassword(Long userId, String oldPassword, String newPassword) {
        DBUser user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthenticationException("Utilisateur non trouvé"));
        
        // Vérifier l'ancien mot de passe
        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw new AuthenticationException("Ancien mot de passe incorrect");
        }
        
        // Valider le nouveau mot de passe
        PasswordUtil.PasswordValidationResult validation = PasswordUtil.validatePassword(newPassword);
        if (!validation.isValid()) {
            throw new IllegalArgumentException("Nouveau mot de passe invalide : " + validation.getFirstError());
        }
        
        // Mettre à jour
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        
        logger.info("🔑 Mot de passe mis à jour pour l'utilisateur: {}", user.getUsername());
    }

    /**
     * Gestion des tentatives de connexion échouées
     */
    private void handleFailedLogin(DBUser user) {
        userRepository.incrementTentativesConnexion(user.getUserId());
        
        if (user.getTentativesConnexion() + 1 >= MAX_LOGIN_ATTEMPTS) {
            userRepository.verrouillerCompte(user.getUserId());
            logger.warn("🔒 Compte verrouillé pour l'utilisateur {} après {} tentatives échouées", 
                       user.getUsername(), MAX_LOGIN_ATTEMPTS);
        }
    }

    /**
     * Gestion des sessions concurrentes
     */
    private void manageConcurrentSessions(Long userId) {
        long activeSessions = sessionRepository.countByUserIdAndActifTrue(userId);
        
        if (activeSessions >= maxSessionsPerUser) {
            // Désactiver les anciennes sessions
            sessionRepository.deactivateUserSessions(userId);
            logger.info("🔄 Sessions anciennes désactivées pour l'utilisateur ID: {} (limite: {})", 
                       userId, maxSessionsPerUser);
        }
    }

    /**
     * Création d'une session utilisateur
     */
    private String createUserSession(DBUser user, String clientIp, String userAgent) {
        String sessionId = UUID.randomUUID().toString();
        
        UserSession session = new UserSession();
        session.setSessionId(sessionId);
        session.setUserId(user.getUserId());
        session.setHotelId(user.getHotel().getHotelId());
        session.setIpAddress(clientIp);
        session.setUserAgent(userAgent);
        session.setDerniereActivite(LocalDateTime.now());
        session.setActif(true);
        
        sessionRepository.save(session);
        
        logger.debug("📝 Session créée: {} pour l'utilisateur: {}", sessionId, user.getUsername());
        
        return sessionId;
    }

    /**
     * Construction de la reponse de connexion.
     *
     * <p>Tour 38 C6 : ajout de {@code expiresIn} (secondes) pour aligner sur OAuth2.</p>
     */
    private LoginResponse buildLoginResponse(String jwt, UserPrincipal userPrincipal, String sessionId) {
        LoginResponse response = new LoginResponse();
        response.setToken(jwt);
        response.setExpiryDate(LocalDateTime.ofInstant(
                tokenProvider.getExpirationDateFromJWT(jwt).toInstant(),
                ZoneId.systemDefault()
        ));
        response.setExpiresIn(jwtExpirationMs / 1000L);
        response.setUserId(userPrincipal.getUserId());
        response.setUsername(userPrincipal.getUsername());
        response.setEmail(userPrincipal.getEmail());
        response.setPrenom(userPrincipal.getPrenom());
        response.setNom(userPrincipal.getNom());
        // Tour 7C : hotelId retiré du DTO ; le front lit le claim JWT "hotelId".
        response.setHotelCode(userPrincipal.getHotelCode());
        response.setHotelNom(userPrincipal.getHotelNom());
        response.setRoleCode(userPrincipal.getRoleCode());
        response.setRoleNom(userPrincipal.getRoleNom());
        response.setSessionId(sessionId);
        response.setDerniereConnexion(LocalDateTime.now());

        return response;
    }

    /**
     * Nettoyage periodique des sessions expirees (a appeler par un scheduler).
     *
     * <p><b>SYSTEM-ROOT (Tour 7B C3)</b> — methode cross-tenant <i>by design</i>.
     * A appeler UNIQUEMENT par un scheduler central en mode ROOT (TenantContext
     * non set). NE JAMAIS exposer en endpoint HTTP : un appel depuis un thread
     * tenant-scope serait equivalent a un escalade de privilege.</p>
     *
     * <p>Garde fail-fast : refuse l'execution si TenantContext est positionne,
     * pour empecher une utilisation accidentelle dans un flux metier.</p>
     */
    @Transactional
    public void cleanupExpiredSessions() {
        if (TenantContext.getOrNull() != null) {
            throw new IllegalStateException(
                    "SYSTEM-ROOT method called from tenant-scoped thread (hotelId="
                    + TenantContext.get() + ") — refused.");
        }
        try {
            // Désactiver les sessions expirées (plus de 30 minutes d'inactivité)
            int expiredSessions = sessionRepository.deactivateExpiredSessions(
                    LocalDateTime.now().minusMinutes(30));

            // Supprimer les anciennes sessions (plus de 30 jours)
            int deletedSessions = sessionRepository.deleteOldSessions(
                    LocalDateTime.now().minusDays(30));

            logger.info("🧹 Nettoyage des sessions: {} expirées, {} supprimées",
                       expiredSessions, deletedSessions);

        } catch (Exception e) {
            logger.error("❌ Erreur lors du nettoyage des sessions", e);
        }
    }

    /**
     * Deverrouillage automatique des comptes (a appeler par un scheduler).
     *
     * <p><b>SYSTEM-ROOT (Tour 7B C3)</b> — methode cross-tenant <i>by design</i>.
     * A appeler UNIQUEMENT par un scheduler central en mode ROOT (TenantContext
     * non set). NE JAMAIS exposer en endpoint HTTP.</p>
     *
     * <p>Garde fail-fast : refuse l'execution si TenantContext est positionne,
     * pour empecher une utilisation accidentelle dans un flux metier.</p>
     */
    @Transactional
    public void unlockExpiredAccounts() {
        if (TenantContext.getOrNull() != null) {
            throw new IllegalStateException(
                    "SYSTEM-ROOT method called from tenant-scoped thread (hotelId="
                    + TenantContext.get() + ") — refused.");
        }
        try {
            // Trouver les comptes verrouillés depuis plus de 24 heures
            var lockedUsers = userRepository.findByCompteVerrouilleTrueAndActifTrue();

            for (DBUser user : lockedUsers) {
                // Logique de déverrouillage automatique si nécessaire
                // Par exemple, après 24 heures
                if (user.getDateModification() != null &&
                    user.getDateModification().isBefore(LocalDateTime.now().minusHours(24))) {

                    userRepository.deverrouillerCompte(user.getUserId());
                    logger.info("🔓 Compte automatiquement déverrouillé pour l'utilisateur: {}",
                               user.getUsername());
                }
            }
        } catch (Exception e) {
            logger.error("❌ Erreur lors du déverrouillage automatique des comptes", e);
        }
    }

    /**
     * Réinitialisation du mot de passe (pour admin)
     */
    @Transactional
    public String resetPassword(Long userId) {
        DBUser user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthenticationException("Utilisateur non trouvé"));
        
        // Générer un nouveau mot de passe temporaire
        String temporaryPassword = PasswordUtil.generateSecurePassword(12);
        
        // Hasher et sauvegarder
        user.setPasswordHash(passwordEncoder.encode(temporaryPassword));
        user.setTentativesConnexion(0);
        user.setCompteVerrouille(false);
        userRepository.save(user);
        
        // Désactiver toutes les sessions actives
        sessionRepository.deactivateUserSessions(userId);
        
        logger.info("🔑 Mot de passe réinitialisé pour l'utilisateur: {}", user.getUsername());
        
        return temporaryPassword;
    }

    /**
     * Vérification de la santé du service d'authentification
     */
    public Map<String, Object> healthCheck() {
        Map<String, Object> health = new HashMap<>();
        
        try {
            // Vérifier la connexion à la base de données
            long userCount = userRepository.count();
            health.put("database", "UP");
            health.put("totalUsers", userCount);
            
            // Vérifier les sessions actives
            long activeSessions = sessionRepository.countByActifTrue();
            health.put("activeSessions", activeSessions);
            
            // Vérifier les comptes verrouillés
            long lockedAccounts = userRepository.findByCompteVerrouilleTrueAndActifTrue().size();
            health.put("lockedAccounts", lockedAccounts);
            
            health.put("status", "UP");
            health.put("timestamp", LocalDateTime.now());
            
        } catch (Exception e) {
            health.put("status", "DOWN");
            health.put("error", e.getMessage());
            health.put("timestamp", LocalDateTime.now());
            logger.error("❌ Erreur lors du health check", e);
        }
        
        return health;
    }
    
    /**
     * Extraction de l'adresse IP du client avec normalisation
     */
    private String getClientIpAddress(HttpServletRequest request) {
        // Essayer plusieurs en-têtes pour récupérer la vraie IP
        String[] headerNames = {
            "X-Forwarded-For",
            "X-Real-IP", 
            "X-Original-Forwarded-For",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_X_FORWARDED_FOR",
            "HTTP_X_FORWARDED",
            "HTTP_X_CLUSTER_CLIENT_IP",
            "HTTP_CLIENT_IP",
            "HTTP_FORWARDED_FOR",
            "HTTP_FORWARDED"
        };
        
        for (String headerName : headerNames) {
            String ip = request.getHeader(headerName);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                // Prendre la première IP si plusieurs sont présentes
                if (ip.contains(",")) {
                    ip = ip.split(",")[0].trim();
                }
                
                // Valider et normaliser l'IP
                String normalizedIp = normalizeIpAddress(ip);
                if (normalizedIp != null) {
                    logger.debug("🌐 IP trouvée via {}: {} -> {}", headerName, ip, normalizedIp);
                    return normalizedIp;
                }
            }
        }
        
        // Fallback vers l'IP de la connexion directe
        String remoteAddr = request.getRemoteAddr();
        String normalizedIp = normalizeIpAddress(remoteAddr);
        
        logger.debug("🌐 IP finale utilisée: {} -> {}", remoteAddr, normalizedIp);
        return normalizedIp != null ? normalizedIp : "127.0.0.1";
    }
    
    /**
     * Normalise une adresse IP pour PostgreSQL inet
     */
    private String normalizeIpAddress(String ipAddress) {
        if (ipAddress == null || ipAddress.trim().isEmpty()) {
            return "127.0.0.1";
        }
        
        String trimmed = ipAddress.trim();
        
        // Cas spéciaux courants
        switch (trimmed) {
            case "0:0:0:0:0:0:0:1":
            case "::1":
                return "::1"; // IPv6 localhost
            case "127.0.0.1":
            case "localhost":
                return "127.0.0.1"; // IPv4 localhost
            case "0.0.0.0":
                return "127.0.0.1"; // IP invalide -> localhost
            default:
                break;
        }
        
        // Validation IPv4
        if (isValidIPv4(trimmed)) {
            return trimmed;
        }
        
        // Validation IPv6
        if (isValidIPv6(trimmed)) {
            return trimmed;
        }
        
        // Si rien ne fonctionne, retourner localhost
        logger.warn("⚠️ Adresse IP invalide '{}', utilisation de localhost", trimmed);
        return "127.0.0.1";
    }
    
    /**
     * Valide une adresse IPv4
     */
    private boolean isValidIPv4(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }
        
        try {
            String[] parts = ip.split("\\.");
            if (parts.length != 4) {
                return false;
            }
            
            for (String part : parts) {
                int num = Integer.parseInt(part);
                if (num < 0 || num > 255) {
                    return false;
                }
            }
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    /**
     * Valide une adresse IPv6
     */
    private boolean isValidIPv6(String ip) {
        if (ip == null || ip.isEmpty() || !ip.contains(":")) {
            return false;
        }
        
        try {
            java.net.InetAddress.getByName(ip);
            return true;
        } catch (java.net.UnknownHostException e) {
            return false;
        }
    }
}
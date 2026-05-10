package com.cityprojects.citybackend.dto.auth;

import java.time.LocalDateTime;

/**
 * DTO pour la réponse de connexion
 */
public class LoginResponse {
    
    private String token;
    private String tokenType = "Bearer";
    private LocalDateTime expiryDate;

    // Tour 38 C6/C7 : refresh token rotation + expiresIn standard OAuth2.
    private String refreshToken;
    private Long expiresIn; // duree en secondes de validite de l'access token

    // Informations utilisateur
    private Long userId;
    private String username;
    private String email;
    private String prenom;
    private String nom;
    private String nomComplet;
    
    // Informations hôtel
    // Note (Tour 7C) : champ hotelId retiré côté API. Le front lit le claim "hotelId"
    // directement dans le JWT pour éviter une exposition redondante du tenant ID.
    private String hotelCode;
    private String hotelNom;
    
    // Informations rôle
    private String roleCode;
    private String roleNom;
    
    // Informations de session
    private String sessionId;
    private LocalDateTime derniereConnexion;

    // Constructeurs
    public LoginResponse() {}

    public LoginResponse(String token, LocalDateTime expiryDate, Long userId, String username,
                        String email, String prenom, String nom, String hotelCode,
                        String hotelNom, String roleCode, String roleNom) {
        this.token = token;
        this.expiryDate = expiryDate;
        this.userId = userId;
        this.username = username;
        this.email = email;
        this.prenom = prenom;
        this.nom = nom;
        this.nomComplet = prenom + " " + nom;
        this.hotelCode = hotelCode;
        this.hotelNom = hotelNom;
        this.roleCode = roleCode;
        this.roleNom = roleNom;
    }

    // Getters et Setters
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public String getTokenType() { return tokenType; }
    public void setTokenType(String tokenType) { this.tokenType = tokenType; }

    public LocalDateTime getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDateTime expiryDate) { this.expiryDate = expiryDate; }

    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }

    public Long getExpiresIn() { return expiresIn; }
    public void setExpiresIn(Long expiresIn) { this.expiresIn = expiresIn; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPrenom() { return prenom; }
    public void setPrenom(String prenom) { 
        this.prenom = prenom; 
        updateNomComplet();
    }

    public String getNom() { return nom; }
    public void setNom(String nom) { 
        this.nom = nom; 
        updateNomComplet();
    }

    public String getNomComplet() { return nomComplet; }
    public void setNomComplet(String nomComplet) { this.nomComplet = nomComplet; }

    public String getHotelCode() { return hotelCode; }
    public void setHotelCode(String hotelCode) { this.hotelCode = hotelCode; }

    public String getHotelNom() { return hotelNom; }
    public void setHotelNom(String hotelNom) { this.hotelNom = hotelNom; }

    public String getRoleCode() { return roleCode; }
    public void setRoleCode(String roleCode) { this.roleCode = roleCode; }

    public String getRoleNom() { return roleNom; }
    public void setRoleNom(String roleNom) { this.roleNom = roleNom; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public LocalDateTime getDerniereConnexion() { return derniereConnexion; }
    public void setDerniereConnexion(LocalDateTime derniereConnexion) { this.derniereConnexion = derniereConnexion; }

    private void updateNomComplet() {
        if (prenom != null && nom != null) {
            this.nomComplet = prenom + " " + nom;
        }
    }
}
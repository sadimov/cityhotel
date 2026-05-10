package com.cityprojects.citybackend.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO pour la requête de connexion
 */
public class LoginRequest {
    
    @NotBlank(message = "Le nom d'utilisateur est obligatoire")
    @Size(min = 3, max = 100, message = "Le nom d'utilisateur doit contenir entre 3 et 100 caractères")
    private String username;
    
    // Tour 38 H3 : aligne sur app.security.password.min-length=8 (PasswordUtil).
    // Refus precoce des passwords trop courts cote API (sans expose la raison
    // detaillee aux attaquants — message neutre).
    @NotBlank(message = "Le mot de passe est obligatoire")
    @Size(min = 8, max = 128, message = "Le mot de passe doit contenir entre 8 et 128 caractères")
    private String password;
    
    private boolean rememberMe = false;

    // Constructeurs
    public LoginRequest() {}

    public LoginRequest(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public LoginRequest(String username, String password, boolean rememberMe) {
        this.username = username;
        this.password = password;
        this.rememberMe = rememberMe;
    }

    // Getters et Setters
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public boolean isRememberMe() { return rememberMe; }
    public void setRememberMe(boolean rememberMe) { this.rememberMe = rememberMe; }
}
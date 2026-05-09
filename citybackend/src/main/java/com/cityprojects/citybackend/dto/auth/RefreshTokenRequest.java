package com.cityprojects.citybackend.dto.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * DTO pour la requête de rafraîchissement de token
 */
public class RefreshTokenRequest {
    
    @NotBlank(message = "Le token est obligatoire")
    private String token;

    // Constructeurs
    public RefreshTokenRequest() {}

    public RefreshTokenRequest(String token) {
        this.token = token;
    }

    // Getters et Setters
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
}
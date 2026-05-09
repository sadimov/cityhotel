package com.cityprojects.citybackend.entity.core;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.LocalDateTime;

/**
 * Entité UserSession - Gestion des sessions utilisateurs
 * Version corrigée pour PostgreSQL avec type inet
 */
@Entity
@Table(name = "user_sessions", schema = "core")
@EntityListeners(AuditingEntityListener.class)
public class UserSession {
    
    @Id
    @Column(name = "session_id", length = 128)
    private String sessionId;
    
    @Column(name = "user_id", nullable = false)
    private Long userId;
    
    @Column(name = "hotel_id", nullable = false)
    private Long hotelId;
    
    // Utilisation du type String avec conversion personnalisée pour PostgreSQL inet
    @Column(name = "ip_address", columnDefinition = "inet")
    private String ipAddress;
    
    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;
    
    @Column(name = "derniere_activite")
    private LocalDateTime derniereActivite = LocalDateTime.now();
    
    @CreatedDate
    @Column(name = "date_creation", updatable = false)
    private LocalDateTime dateCreation;
    
    @Column(name = "actif")
    private Boolean actif = true;
    
    // Relations
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private DBUser user;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hotel_id", insertable = false, updatable = false)
    private Hotel hotel;
    
    // Constructeurs
    public UserSession() {}
    
    public UserSession(String sessionId, Long userId, Long hotelId, String ipAddress, String userAgent) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.hotelId = hotelId;
        this.ipAddress = normalizeIpAddress(ipAddress);
        this.userAgent = userAgent;
    }
    
    // Getters et Setters
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    
    public Long getHotelId() { return hotelId; }
    public void setHotelId(Long hotelId) { this.hotelId = hotelId; }
    
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { 
        this.ipAddress = normalizeIpAddress(ipAddress); 
    }
    
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    
    public LocalDateTime getDerniereActivite() { return derniereActivite; }
    public void setDerniereActivite(LocalDateTime derniereActivite) { this.derniereActivite = derniereActivite; }
    
    public LocalDateTime getDateCreation() { return dateCreation; }
    public void setDateCreation(LocalDateTime dateCreation) { this.dateCreation = dateCreation; }
    
    public Boolean getActif() { return actif; }
    public void setActif(Boolean actif) { this.actif = actif; }
    
    public DBUser getUser() { return user; }
    public void setUser(DBUser user) { this.user = user; }
    
    public Hotel getHotel() { return hotel; }
    public void setHotel(Hotel hotel) { this.hotel = hotel; }
    
    /**
     * Met à jour l'heure de dernière activité
     */
    public void updateActivity() {
        this.derniereActivite = LocalDateTime.now();
    }
    
    /**
     * Vérifie si la session est expirée (plus de 30 minutes d'inactivité)
     */
    public boolean isExpired() {
        return derniereActivite.isBefore(LocalDateTime.now().minusMinutes(30));
    }
    
    /**
     * Normalise l'adresse IP pour PostgreSQL inet
     */
    private String normalizeIpAddress(String ipAddress) {
        if (ipAddress == null || ipAddress.trim().isEmpty()) {
            return "127.0.0.1"; // Valeur par défaut
        }
        
        String trimmed = ipAddress.trim();
        
        // Gérer les cas spéciaux
        if ("0:0:0:0:0:0:0:1".equals(trimmed) || "::1".equals(trimmed)) {
            return "::1"; // IPv6 localhost normalisé
        }
        
        if ("127.0.0.1".equals(trimmed) || "localhost".equals(trimmed)) {
            return "127.0.0.1"; // IPv4 localhost
        }
        
        // Validation basique IPv4
        if (isValidIPv4(trimmed)) {
            return trimmed;
        }
        
        // Validation basique IPv6
        if (isValidIPv6(trimmed)) {
            return trimmed;
        }
        
        // Si invalide, retourner localhost
        return "127.0.0.1";
    }
    
    /**
     * Validation simple IPv4
     */
    private boolean isValidIPv4(String ip) {
        try {
            String[] parts = ip.split("\\.");
            if (parts.length != 4) return false;
            
            for (String part : parts) {
                int num = Integer.parseInt(part);
                if (num < 0 || num > 255) return false;
            }
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    /**
     * Validation simple IPv6
     */
    private boolean isValidIPv6(String ip) {
        try {
            InetAddress.getByName(ip);
            return ip.contains(":");
        } catch (UnknownHostException e) {
            return false;
        }
    }
    
    /**
     * Obtient l'adresse IP formatée pour l'affichage
     */
    public String getDisplayIpAddress() {
        if (ipAddress == null) return "Inconnue";
        
        if ("::1".equals(ipAddress) || "127.0.0.1".equals(ipAddress)) {
            return "Localhost";
        }
        
        return ipAddress;
    }
    
    /**
     * Vérifie si la session provient du localhost
     */
    public boolean isLocalhost() {
        return "::1".equals(ipAddress) || "127.0.0.1".equals(ipAddress);
    }
    
    @Override
    public String toString() {
        return "UserSession{" +
                "sessionId='" + sessionId + '\'' +
                ", userId=" + userId +
                ", hotelId=" + hotelId +
                ", ipAddress='" + ipAddress + '\'' +
                ", derniereActivite=" + derniereActivite +
                ", actif=" + actif +
                '}';
    }
}
package com.cityprojects.citybackend.entity.core;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Entité DBUser - Utilisateurs du système
 * Version sans colonne salt (BCrypt inclut le salt dans le hash)
 */
@Entity
@Table(name = "dbusers", schema = "core")
@EntityListeners(AuditingEntityListener.class)
public class DBUser {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;
    
    @Column(name = "username", unique = true, nullable = false, length = 100)
    @NotBlank(message = "Le nom d'utilisateur est obligatoire")
    @Size(min = 3, max = 100, message = "Le nom d'utilisateur doit contenir entre 3 et 100 caractères")
    private String username;
    
    @Column(name = "email", unique = true, nullable = false)
    @NotBlank(message = "L'email est obligatoire")
    @Email(message = "Format d'email invalide")
    private String email;
    
    @Column(name = "password_hash", nullable = false)
    @NotBlank(message = "Le mot de passe est obligatoire")
    private String passwordHash;
    
    @Column(name = "prenom", nullable = false, length = 100)
    @NotBlank(message = "Le prénom est obligatoire")
    private String prenom;
    
    @Column(name = "nom", nullable = false, length = 100)
    @NotBlank(message = "Le nom est obligatoire")
    private String nom;
    
    @Column(name = "telephone", length = 20)
    private String telephone;
    
    @Column(name = "poste", length = 100)
    private String poste;
    
    @Column(name = "actif")
    private Boolean actif = true;
    
    @Column(name = "derniere_connexion")
    private LocalDateTime derniereConnexion;
    
    @Column(name = "tentatives_connexion")
    private Integer tentativesConnexion = 0;
    
    @Column(name = "compte_verrouille")
    private Boolean compteVerrouille = false;
    
    @CreatedDate
    @Column(name = "date_creation", updatable = false)
    private LocalDateTime dateCreation;
    
    @LastModifiedDate
    @Column(name = "date_modification")
    private LocalDateTime dateModification;
    
    // Relations
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hotel_id", nullable = false)
    private Hotel hotel;
    
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;
    
    // Constructeurs
    public DBUser() {}
    
    public DBUser(String username, String email, String passwordHash, 
                  String prenom, String nom, Hotel hotel, Role role) {
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.prenom = prenom;
        this.nom = nom;
        this.hotel = hotel;
        this.role = role;
    }
    
    // Getters et Setters
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    
    public String getPrenom() { return prenom; }
    public void setPrenom(String prenom) { this.prenom = prenom; }
    
    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }
    
    public String getTelephone() { return telephone; }
    public void setTelephone(String telephone) { this.telephone = telephone; }
    
    public String getPoste() { return poste; }
    public void setPoste(String poste) { this.poste = poste; }
    
    public Boolean getActif() { return actif; }
    public void setActif(Boolean actif) { this.actif = actif; }
    
    public LocalDateTime getDerniereConnexion() { return derniereConnexion; }
    public void setDerniereConnexion(LocalDateTime derniereConnexion) { this.derniereConnexion = derniereConnexion; }
    
    public Integer getTentativesConnexion() { return tentativesConnexion; }
    public void setTentativesConnexion(Integer tentativesConnexion) { this.tentativesConnexion = tentativesConnexion; }
    
    public Boolean getCompteVerrouille() { return compteVerrouille; }
    public void setCompteVerrouille(Boolean compteVerrouille) { this.compteVerrouille = compteVerrouille; }
    
    public LocalDateTime getDateCreation() { return dateCreation; }
    public void setDateCreation(LocalDateTime dateCreation) { this.dateCreation = dateCreation; }
    
    public LocalDateTime getDateModification() { return dateModification; }
    public void setDateModification(LocalDateTime dateModification) { this.dateModification = dateModification; }
    
    public Hotel getHotel() { return hotel; }
    public void setHotel(Hotel hotel) { this.hotel = hotel; }
    
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    
    /**
     * Nom complet de l'utilisateur
     */
    public String getNomComplet() {
        return prenom + " " + nom;
    }
    
    /**
     * Vérifie si l'utilisateur a un rôle spécifique
     */
    public boolean hasRole(String roleCode) {
        return role != null && roleCode.equals(role.getRoleCode());
    }
    
    /**
     * Vérifie si l'utilisateur peut accéder à un hôtel spécifique
     */
    public boolean canAccessHotel(Long hotelId) {
        return hotel != null && hotel.getHotelId().equals(hotelId);
    }
    
    @Override
    public String toString() {
        return "DBUser{" +
                "userId=" + userId +
                ", username='" + username + '\'' +
                ", email='" + email + '\'' +
                ", prenom='" + prenom + '\'' +
                ", nom='" + nom + '\'' +
                ", actif=" + actif +
                ", hotelId=" + (hotel != null ? hotel.getHotelId() : null) +
                ", roleCode=" + (role != null ? role.getRoleCode() : null) +
                '}';
    }
}
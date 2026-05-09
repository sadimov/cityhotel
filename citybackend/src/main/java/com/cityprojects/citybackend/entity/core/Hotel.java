package com.cityprojects.citybackend.entity.core;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Entité Hotel - Table centrale du système multi-tenant
 */
@Entity
@Table(name = "hotels", schema = "core")
@EntityListeners(AuditingEntityListener.class)
public class Hotel {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "hotel_id")
    private Long hotelId;
    
    @Column(name = "hotel_code", unique = true, nullable = false, length = 10)
    @NotBlank(message = "Le code hôtel est obligatoire")
    @Size(max = 10, message = "Le code hôtel ne peut pas dépasser 10 caractères")
    private String hotelCode;
    
    @Column(name = "hotel_nom", nullable = false)
    @NotBlank(message = "Le nom de l'hôtel est obligatoire")
    @Size(max = 255, message = "Le nom ne peut pas dépasser 255 caractères")
    private String hotelNom;
    
    @Column(name = "hotel_adresse", columnDefinition = "TEXT")
    private String hotelAdresse;
    
    @Column(name = "hotel_tel", length = 50)
    private String hotelTel;
    
    @Column(name = "logo_url", length = 500)
    private String logoUrl;
    
    @Column(name = "ville", length = 100)
    private String ville;
    
    @Column(name = "pays", length = 100)
    private String pays;
    
    @Column(name = "boite_postale", length = 20)
    private String boitePostale;
    
    @Column(name = "email", length = 100)
    @Email(message = "Format d'email invalide")
    private String email;
    
    @Column(name = "site_web", length = 200)
    private String siteWeb;
    
    @Column(name = "devise", length = 3)
    private String devise = "MRU";

    /**
     * Code pays ISO 3166 alpha-2 (2 lettres majuscules : MR, FR, SN, MA...).
     * Distinct de {@link #devise} (ISO 4217) car un hotel peut etre situe
     * dans un pays donne mais facturer dans une autre devise.
     * Utilise par le format de numerotation comptable
     * ({@code FACT-2026-MR-000001}). Defaut 'MR' (Mauritanie), marche cible.
     */
    @Column(name = "code_pays", length = 2, nullable = false)
    private String codePays = "MR";

    @Column(name = "fuseau_horaire", length = 50)
    private String fuseauHoraire = "Africa/Nouakchott";
    
    @Column(name = "actif")
    private Boolean actif = true;
    
    @CreatedDate
    @Column(name = "date_creation", updatable = false)
    private LocalDateTime dateCreation;
    
    @LastModifiedDate
    @Column(name = "date_modification")
    private LocalDateTime dateModification;
    
    // Relations
    @OneToMany(mappedBy = "hotel", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<DBUser> users = new HashSet<>();
    
    // Constructeurs
    public Hotel() {}
    
    public Hotel(String hotelCode, String hotelNom) {
        this.hotelCode = hotelCode;
        this.hotelNom = hotelNom;
    }
    
    // Getters et Setters
    public Long getHotelId() { return hotelId; }
    public void setHotelId(Long hotelId) { this.hotelId = hotelId; }
    
    public String getHotelCode() { return hotelCode; }
    public void setHotelCode(String hotelCode) { this.hotelCode = hotelCode; }
    
    public String getHotelNom() { return hotelNom; }
    public void setHotelNom(String hotelNom) { this.hotelNom = hotelNom; }
    
    public String getHotelAdresse() { return hotelAdresse; }
    public void setHotelAdresse(String hotelAdresse) { this.hotelAdresse = hotelAdresse; }
    
    public String getHotelTel() { return hotelTel; }
    public void setHotelTel(String hotelTel) { this.hotelTel = hotelTel; }
    
    public String getLogoUrl() { return logoUrl; }
    public void setLogoUrl(String logoUrl) { this.logoUrl = logoUrl; }
    
    public String getVille() { return ville; }
    public void setVille(String ville) { this.ville = ville; }
    
    public String getPays() { return pays; }
    public void setPays(String pays) { this.pays = pays; }
    
    public String getBoitePostale() { return boitePostale; }
    public void setBoitePostale(String boitePostale) { this.boitePostale = boitePostale; }
    
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    
    public String getSiteWeb() { return siteWeb; }
    public void setSiteWeb(String siteWeb) { this.siteWeb = siteWeb; }
    
    public String getDevise() { return devise; }
    public void setDevise(String devise) { this.devise = devise; }

    public String getCodePays() { return codePays; }
    public void setCodePays(String codePays) { this.codePays = codePays; }

    public String getFuseauHoraire() { return fuseauHoraire; }
    public void setFuseauHoraire(String fuseauHoraire) { this.fuseauHoraire = fuseauHoraire; }
    
    public Boolean getActif() { return actif; }
    public void setActif(Boolean actif) { this.actif = actif; }
    
    public LocalDateTime getDateCreation() { return dateCreation; }
    public void setDateCreation(LocalDateTime dateCreation) { this.dateCreation = dateCreation; }
    
    public LocalDateTime getDateModification() { return dateModification; }
    public void setDateModification(LocalDateTime dateModification) { this.dateModification = dateModification; }
    
    public Set<DBUser> getUsers() { return users; }
    public void setUsers(Set<DBUser> users) { this.users = users; }
}
package com.cityprojects.citybackend.entity.menage;

import com.cityprojects.citybackend.common.audit.AuditableEntity;
import com.cityprojects.citybackend.common.tenant.TenantAware;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.TenantId;

import java.time.LocalDate;

/**
 * Agent du personnel de menage de l'hotel.
 *
 * <h3>Numerotation</h3>
 * <p>{@code numeroEmploye} est saisi librement (pas de
 * {@link com.cityprojects.citybackend.service.finance.NumerotationService}).
 * Unicite garantie par hotel via {@code uk_personnel_hotel_numero}. Convention
 * libre cote utilisateur (ex. "MEN001", "RH-2024-15").</p>
 *
 * <h3>Multi-tenancy</h3>
 * <p>{@link TenantId @TenantId} sur {@code hotelId} : Hibernate populate la
 * valeur depuis le {@code TenantContext} a l'INSERT et ajoute
 * {@code WHERE hotel_id = ?} a toutes les requetes.</p>
 *
 * <h3>Specialites</h3>
 * <p>{@code specialites} est une chaine libre (typiquement JSON cote front,
 * stocke en VARCHAR(500) - pas de validation cote back, le front gere).</p>
 */
@Entity
@Table(
        name = "personnel",
        schema = "menage",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_personnel_hotel_numero",
                columnNames = {"hotel_id", "numero_employe"}))
public class Personnel extends AuditableEntity implements TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "personnel_id")
    private Long personnelId;

    @TenantId
    @Column(name = "hotel_id", nullable = false, updatable = false)
    private Long hotelId;

    @NotBlank
    @Size(max = 20)
    @Column(name = "numero_employe", nullable = false, length = 20)
    private String numeroEmploye;

    @NotBlank
    @Size(max = 100)
    @Column(name = "prenom", nullable = false, length = 100)
    private String prenom;

    @NotBlank
    @Size(max = 100)
    @Column(name = "nom", nullable = false, length = 100)
    private String nom;

    @Size(max = 20)
    @Column(name = "telephone", length = 20)
    private String telephone;

    @Email
    @Size(max = 100)
    @Column(name = "email", length = 100)
    private String email;

    @Column(name = "date_embauche")
    private LocalDate dateEmbauche;

    @Size(max = 500)
    @Column(name = "specialites", length = 500)
    private String specialites;

    @Column(name = "actif", nullable = false)
    private Boolean actif = Boolean.TRUE;

    /** Constructeur JPA. */
    public Personnel() {
    }

    public Long getPersonnelId() {
        return personnelId;
    }

    public void setPersonnelId(Long personnelId) {
        this.personnelId = personnelId;
    }

    @Override
    public Long getHotelId() {
        return hotelId;
    }

    @Override
    public void setHotelId(Long hotelId) {
        this.hotelId = hotelId;
    }

    public String getNumeroEmploye() {
        return numeroEmploye;
    }

    public void setNumeroEmploye(String numeroEmploye) {
        this.numeroEmploye = numeroEmploye;
    }

    public String getPrenom() {
        return prenom;
    }

    public void setPrenom(String prenom) {
        this.prenom = prenom;
    }

    public String getNom() {
        return nom;
    }

    public void setNom(String nom) {
        this.nom = nom;
    }

    public String getTelephone() {
        return telephone;
    }

    public void setTelephone(String telephone) {
        this.telephone = telephone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public LocalDate getDateEmbauche() {
        return dateEmbauche;
    }

    public void setDateEmbauche(LocalDate dateEmbauche) {
        this.dateEmbauche = dateEmbauche;
    }

    public String getSpecialites() {
        return specialites;
    }

    public void setSpecialites(String specialites) {
        this.specialites = specialites;
    }

    public Boolean getActif() {
        return actif;
    }

    public void setActif(Boolean actif) {
        this.actif = actif;
    }

    /** Helper applicatif (non persiste). */
    public String getNomComplet() {
        return prenom + " " + nom;
    }
}

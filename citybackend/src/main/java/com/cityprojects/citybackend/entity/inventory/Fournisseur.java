package com.cityprojects.citybackend.entity.inventory;

import com.cityprojects.citybackend.common.audit.AuditableEntity;
import com.cityprojects.citybackend.common.tenant.TenantAware;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.TenantId;

/**
 * Fournisseur de l'hotel (matieres premieres, F&amp;B, materiel, services...).
 *
 * <p>Isolation tenant via {@code hotelId} annote {@link TenantId} : Hibernate
 * filtre automatiquement WHERE hotel_id = ? sur toutes les requetes.</p>
 *
 * <h3>Anti-patterns importants</h3>
 * <ul>
 *   <li>NE JAMAIS appeler {@code setHotelId(...)} dans un service metier.</li>
 *   <li>Pas de {@code @ManyToOne} eager vers les bons de commande (eviter les cycles).</li>
 * </ul>
 */
@Entity
@Table(name = "fournisseurs", schema = "inventory")
public class Fournisseur extends AuditableEntity implements TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "fournisseur_id")
    private Long fournisseurId;

    @TenantId
    @Column(name = "hotel_id", nullable = false, updatable = false)
    private Long hotelId;

    @NotBlank
    @Size(max = 255)
    @Column(name = "nom_fournisseur", nullable = false, length = 255)
    private String nomFournisseur;

    @Size(max = 200)
    @Column(name = "contact_principal", length = 200)
    private String contactPrincipal;

    @Size(max = 20)
    @Column(name = "telephone", length = 20)
    private String telephone;

    @Email
    @Size(max = 100)
    @Column(name = "email", length = 100)
    private String email;

    @Column(name = "adresse", columnDefinition = "TEXT")
    private String adresse;

    @Size(max = 100)
    @Column(name = "ville", length = 100)
    private String ville;

    @Size(max = 100)
    @Column(name = "pays", length = 100)
    private String pays;

    @Column(name = "conditions_paiement", columnDefinition = "TEXT")
    private String conditionsPaiement;

    @Column(name = "actif", nullable = false)
    private Boolean actif = Boolean.TRUE;

    /** Constructeur JPA. */
    public Fournisseur() {
    }

    public Long getFournisseurId() {
        return fournisseurId;
    }

    public void setFournisseurId(Long fournisseurId) {
        this.fournisseurId = fournisseurId;
    }

    @Override
    public Long getHotelId() {
        return hotelId;
    }

    @Override
    public void setHotelId(Long hotelId) {
        this.hotelId = hotelId;
    }

    public String getNomFournisseur() {
        return nomFournisseur;
    }

    public void setNomFournisseur(String nomFournisseur) {
        this.nomFournisseur = nomFournisseur;
    }

    public String getContactPrincipal() {
        return contactPrincipal;
    }

    public void setContactPrincipal(String contactPrincipal) {
        this.contactPrincipal = contactPrincipal;
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

    public String getAdresse() {
        return adresse;
    }

    public void setAdresse(String adresse) {
        this.adresse = adresse;
    }

    public String getVille() {
        return ville;
    }

    public void setVille(String ville) {
        this.ville = ville;
    }

    public String getPays() {
        return pays;
    }

    public void setPays(String pays) {
        this.pays = pays;
    }

    public String getConditionsPaiement() {
        return conditionsPaiement;
    }

    public void setConditionsPaiement(String conditionsPaiement) {
        this.conditionsPaiement = conditionsPaiement;
    }

    public Boolean getActif() {
        return actif;
    }

    public void setActif(Boolean actif) {
        this.actif = actif;
    }
}

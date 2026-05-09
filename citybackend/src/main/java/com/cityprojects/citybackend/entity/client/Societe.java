package com.cityprojects.citybackend.entity.client;

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
import org.hibernate.annotations.TenantId;

/**
 * Societe (entreprise) cliente d'un hotel.
 * <p>
 * Une societe peut regrouper plusieurs clients personnes physiques (cf.
 * {@link Client#getSocieteId()}). Isolation tenant via le champ {@code hotelId}
 * annote {@link TenantId} : Hibernate filtre automatiquement chaque
 * SELECT/UPDATE/DELETE par {@code WHERE hotel_id = ?} et populate la valeur
 * a l'INSERT depuis {@link com.cityprojects.citybackend.common.tenant.CityTenantIdentifierResolver}.
 *
 * <p><b>Anti-patterns importants</b> (cf. CLAUDE.md racine §10) :</p>
 * <ul>
 *   <li>NE JAMAIS appeler {@code setHotelId(...)} dans un service metier — la
 *       valeur vient du resolver, pas d'un DTO HTTP.</li>
 *   <li>Pas de {@code @Data} Lombok : equals/hashCode mutables dangereux sur
 *       entite JPA (cf. {@link AuditableEntity}).</li>
 * </ul>
 *
 * <p>Audit (created_at, updated_at, created_by, updated_by) herite de
 * {@link AuditableEntity}.</p>
 */
@Entity
@Table(name = "societes", schema = "client")
public class Societe extends AuditableEntity implements TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "societe_id")
    private Long societeId;

    @TenantId
    @Column(name = "hotel_id", nullable = false, updatable = false)
    private Long hotelId;

    @NotBlank
    @Column(name = "societe_nom", nullable = false, length = 255)
    private String societeNom;

    @Column(name = "siret", length = 20)
    private String siret;

    @Column(name = "adresse", columnDefinition = "TEXT")
    private String adresse;

    @Column(name = "ville", length = 100)
    private String ville;

    @Column(name = "pays", length = 100)
    private String pays;

    @Column(name = "telephone", length = 20)
    private String telephone;

    @Email
    @Column(name = "email", length = 100)
    private String email;

    @Column(name = "contact_principal", length = 200)
    private String contactPrincipal;

    @Column(name = "actif", nullable = false)
    private Boolean actif = Boolean.TRUE;

    /** Constructeur JPA. */
    public Societe() {
    }

    public Long getSocieteId() {
        return societeId;
    }

    public void setSocieteId(Long societeId) {
        this.societeId = societeId;
    }

    @Override
    public Long getHotelId() {
        return hotelId;
    }

    @Override
    public void setHotelId(Long hotelId) {
        this.hotelId = hotelId;
    }

    public String getSocieteNom() {
        return societeNom;
    }

    public void setSocieteNom(String societeNom) {
        this.societeNom = societeNom;
    }

    public String getSiret() {
        return siret;
    }

    public void setSiret(String siret) {
        this.siret = siret;
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

    public String getContactPrincipal() {
        return contactPrincipal;
    }

    public void setContactPrincipal(String contactPrincipal) {
        this.contactPrincipal = contactPrincipal;
    }

    public Boolean getActif() {
        return actif;
    }

    public void setActif(Boolean actif) {
        this.actif = actif;
    }
}

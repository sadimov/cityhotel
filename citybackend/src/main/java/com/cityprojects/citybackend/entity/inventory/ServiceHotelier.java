package com.cityprojects.citybackend.entity.inventory;

import com.cityprojects.citybackend.common.audit.AuditableEntity;
import com.cityprojects.citybackend.common.tenant.TenantAware;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.TenantId;

import java.math.BigDecimal;

/**
 * Service hotelier facture au client durant son sejour : prestation ponctuelle
 * (spa, blanchisserie, transfert aeroport, location de salle, etc.) avec un
 * prix unitaire et une unite de facturation.
 *
 * <p>Isolation tenant via {@code hotelId} annote {@link TenantId}.
 * {@code code} unique par hotel ({@code UNIQUE (hotel_id, code)}).</p>
 *
 * <p>Reference une {@link TypeServiceHotelier} (FK) pour la categorisation.</p>
 *
 * <h3>Integration finance</h3>
 * <p>Consomme par {@code LigneFacture} (type {@code SERVICE}) cote module
 * finance ; aucune relation JPA bidirectionnelle (cross-module). Le
 * {@code serviceId} est passe en tant que reference dans la ligne de facture
 * cote service finance.</p>
 */
@Entity
@Table(
        name = "services_hoteliers",
        schema = "inventory",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_services_hoteliers_hotel_code",
                columnNames = {"hotel_id", "code"}))
public class ServiceHotelier extends AuditableEntity implements TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "service_id")
    private Long serviceId;

    @TenantId
    @Column(name = "hotel_id", nullable = false, updatable = false)
    private Long hotelId;

    @NotNull
    @Column(name = "type_service_id", nullable = false)
    private Long typeServiceId;

    @NotBlank
    @Size(max = 20)
    @Column(name = "code", nullable = false, length = 20)
    private String code;

    @NotBlank
    @Size(max = 255)
    @Column(name = "nom", nullable = false, length = 255)
    private String nom;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = true)
    @Column(name = "prix_unitaire", nullable = false, precision = 10, scale = 2)
    private BigDecimal prixUnitaire = BigDecimal.ZERO;

    @NotBlank
    @Size(max = 20)
    @Column(name = "unite", nullable = false, length = 20)
    private String unite;

    @Column(name = "actif", nullable = false)
    private Boolean actif = Boolean.TRUE;

    /** Constructeur JPA. */
    public ServiceHotelier() {
    }

    public Long getServiceId() {
        return serviceId;
    }

    public void setServiceId(Long serviceId) {
        this.serviceId = serviceId;
    }

    @Override
    public Long getHotelId() {
        return hotelId;
    }

    @Override
    public void setHotelId(Long hotelId) {
        this.hotelId = hotelId;
    }

    public Long getTypeServiceId() {
        return typeServiceId;
    }

    public void setTypeServiceId(Long typeServiceId) {
        this.typeServiceId = typeServiceId;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getNom() {
        return nom;
    }

    public void setNom(String nom) {
        this.nom = nom;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getPrixUnitaire() {
        return prixUnitaire;
    }

    public void setPrixUnitaire(BigDecimal prixUnitaire) {
        this.prixUnitaire = prixUnitaire;
    }

    public String getUnite() {
        return unite;
    }

    public void setUnite(String unite) {
        this.unite = unite;
    }

    public Boolean getActif() {
        return actif;
    }

    public void setActif(Boolean actif) {
        this.actif = actif;
    }
}

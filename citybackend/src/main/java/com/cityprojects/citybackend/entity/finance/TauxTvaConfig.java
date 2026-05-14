package com.cityprojects.citybackend.entity.finance;

import com.cityprojects.citybackend.common.audit.AuditableEntity;
import com.cityprojects.citybackend.common.tenant.TenantAware;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.TenantId;

import java.math.BigDecimal;

/**
 * Configuration TVA par hotel et par type de service (Bloc B4).
 *
 * <p>Tenant-aware via {@code @TenantId}. Chaque hotel peut surcharger les
 * taux par defaut codes par {@link TypeServiceTva#defaultTaux()} selon son
 * regime fiscal (touristique, conventionne, etc.).</p>
 *
 * <p>Unicite : {@code (hotel_id, type_service)}. Seed automatique a la
 * creation d'un hotel via {@code TauxTvaConfigInitializer}.</p>
 */
@Entity
@Table(
        name = "taux_tva_config",
        schema = "finance",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_taux_tva_config_hotel_type",
                columnNames = {"hotel_id", "type_service"}))
public class TauxTvaConfig extends AuditableEntity implements TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @TenantId
    @Column(name = "hotel_id", nullable = false, updatable = false)
    private Long hotelId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "type_service", nullable = false, length = 40)
    private TypeServiceTva typeService;

    /**
     * Taux TVA en pourcentage. Entre 0.00 (exonere) et 99.99 inclus.
     * Calcul applique : {@code montantTva = montantHt * taux / 100}.
     */
    @NotNull
    @DecimalMin(value = "0.00", message = "error.tva.taux.negatif")
    @DecimalMax(value = "99.99", message = "error.tva.taux.trop.eleve")
    @Column(name = "taux", nullable = false, precision = 5, scale = 2)
    private BigDecimal taux;

    @NotNull
    @Column(name = "actif", nullable = false)
    private Boolean actif = Boolean.TRUE;

    @Size(max = 200)
    @Column(name = "libelle", length = 200)
    private String libelle;

    /** Constructeur JPA. */
    public TauxTvaConfig() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    @Override
    public Long getHotelId() {
        return hotelId;
    }

    @Override
    public void setHotelId(Long hotelId) {
        this.hotelId = hotelId;
    }

    public TypeServiceTva getTypeService() {
        return typeService;
    }

    public void setTypeService(TypeServiceTva typeService) {
        this.typeService = typeService;
    }

    public BigDecimal getTaux() {
        return taux;
    }

    public void setTaux(BigDecimal taux) {
        this.taux = taux;
    }

    public Boolean getActif() {
        return actif;
    }

    public void setActif(Boolean actif) {
        this.actif = actif;
    }

    public String getLibelle() {
        return libelle;
    }

    public void setLibelle(String libelle) {
        this.libelle = libelle;
    }
}

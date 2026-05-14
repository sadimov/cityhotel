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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.TenantId;

/**
 * Type/categorie de service hotelier propose au client (Restauration, Bien-etre,
 * Transport, Blanchisserie, Autre...). Permet de regrouper les
 * {@link ServiceHotelier} par famille metier.
 *
 * <p>Isolation tenant via {@code hotelId} annote {@link TenantId}.
 * {@code code} unique par hotel ({@code UNIQUE (hotel_id, code)}).</p>
 */
@Entity
@Table(
        name = "types_services_hoteliers",
        schema = "inventory",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_types_services_hoteliers_hotel_code",
                columnNames = {"hotel_id", "code"}))
public class TypeServiceHotelier extends AuditableEntity implements TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "type_service_id")
    private Long typeServiceId;

    @TenantId
    @Column(name = "hotel_id", nullable = false, updatable = false)
    private Long hotelId;

    @NotBlank
    @Size(max = 20)
    @Column(name = "code", nullable = false, length = 20)
    private String code;

    @NotBlank
    @Size(max = 100)
    @Column(name = "nom", nullable = false, length = 100)
    private String nom;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "actif", nullable = false)
    private Boolean actif = Boolean.TRUE;

    /** Constructeur JPA. */
    public TypeServiceHotelier() {
    }

    public Long getTypeServiceId() {
        return typeServiceId;
    }

    public void setTypeServiceId(Long typeServiceId) {
        this.typeServiceId = typeServiceId;
    }

    @Override
    public Long getHotelId() {
        return hotelId;
    }

    @Override
    public void setHotelId(Long hotelId) {
        this.hotelId = hotelId;
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

    public Boolean getActif() {
        return actif;
    }

    public void setActif(Boolean actif) {
        this.actif = actif;
    }
}

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
 * Categorie de produits (regroupement metier : F&amp;B, fournitures, hygiene, etc.).
 *
 * <p>Isolation tenant via {@code hotelId} annote {@link TenantId}. {@code code_categorie}
 * unique par hotel - utile pour les imports/exports CSV (UNIQUE (hotel_id, code_categorie)).</p>
 */
@Entity
@Table(
        name = "categories_produits",
        schema = "inventory",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_categories_produits_hotel_code",
                columnNames = {"hotel_id", "code_categorie"}))
public class CategorieProduit extends AuditableEntity implements TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "categorie_id")
    private Long categorieId;

    @TenantId
    @Column(name = "hotel_id", nullable = false, updatable = false)
    private Long hotelId;

    @NotBlank
    @Size(max = 10)
    @Column(name = "code_categorie", nullable = false, length = 10)
    private String codeCategorie;

    @NotBlank
    @Size(max = 100)
    @Column(name = "nom_categorie", nullable = false, length = 100)
    private String nomCategorie;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "actif", nullable = false)
    private Boolean actif = Boolean.TRUE;

    /** Constructeur JPA. */
    public CategorieProduit() {
    }

    public Long getCategorieId() {
        return categorieId;
    }

    public void setCategorieId(Long categorieId) {
        this.categorieId = categorieId;
    }

    @Override
    public Long getHotelId() {
        return hotelId;
    }

    @Override
    public void setHotelId(Long hotelId) {
        this.hotelId = hotelId;
    }

    public String getCodeCategorie() {
        return codeCategorie;
    }

    public void setCodeCategorie(String codeCategorie) {
        this.codeCategorie = codeCategorie;
    }

    public String getNomCategorie() {
        return nomCategorie;
    }

    public void setNomCategorie(String nomCategorie) {
        this.nomCategorie = nomCategorie;
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

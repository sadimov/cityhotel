package com.cityprojects.citybackend.entity.restaurant;

import com.cityprojects.citybackend.common.audit.AuditableEntity;
import com.cityprojects.citybackend.common.tenant.TenantAware;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.TenantId;

/**
 * Categorie de menu (Entrees, Plats, Boissons, Desserts, ...).
 *
 * <p>Catalogue restaurant - Tour 23 (catalogue uniquement, POS reporte au
 * Tour 24+).</p>
 *
 * <p>Isolation tenant via {@code hotelId} annote {@link TenantId}. Aucun
 * service ne doit setter {@code hotelId} explicitement (Hibernate populate via
 * le resolver a l'INSERT).</p>
 *
 * <p>Champ {@code ordre} : positionnement dans l'interface POS (les categories
 * sont triees par {@code ordre} ASC, puis {@code nom}). {@code iconeUrl} :
 * optionnel, pour affichage des cartes du POS.</p>
 */
@Entity
@Table(name = "categories_menus", schema = "restaurant")
public class CategorieMenu extends AuditableEntity implements TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "categorie_id")
    private Long categorieId;

    @TenantId
    @Column(name = "hotel_id", nullable = false, updatable = false)
    private Long hotelId;

    @NotBlank
    @Size(max = 100)
    @Column(name = "nom", nullable = false, length = 100)
    private String nom;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Size(max = 200)
    @Column(name = "icone_url", length = 200)
    private String iconeUrl;

    @Column(name = "ordre", nullable = false)
    private Integer ordre = 0;

    @Column(name = "actif", nullable = false)
    private Boolean actif = Boolean.TRUE;

    /** Constructeur JPA. */
    public CategorieMenu() {
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

    public String getIconeUrl() {
        return iconeUrl;
    }

    public void setIconeUrl(String iconeUrl) {
        this.iconeUrl = iconeUrl;
    }

    public Integer getOrdre() {
        return ordre;
    }

    public void setOrdre(Integer ordre) {
        this.ordre = ordre;
    }

    public Boolean getActif() {
        return actif;
    }

    public void setActif(Boolean actif) {
        this.actif = actif;
    }
}

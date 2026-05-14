package com.cityprojects.citybackend.entity.hebergement;

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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.TenantId;

import java.math.BigDecimal;

/**
 * Catalogue des types de chambres par hotel (Standard, Deluxe, Suite, ...).
 *
 * <p>Le {@code typeCode} est une cle metier locale a l'hotel (ex. "STD", "DLX",
 * "STE"). Unique par hotel via {@code uk_types_chambres_hotel_code}.</p>
 *
 * <p>Isolation tenant via {@code hotelId} annote {@link TenantId}. Aucun service
 * ne doit jamais setter {@code hotelId} explicitement (Hibernate populate via le
 * resolver a l'INSERT).</p>
 */
@Entity
@Table(
        name = "types_chambres",
        schema = "hebergement",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_types_chambres_hotel_code",
                columnNames = {"hotel_id", "type_code"}))
public class TypeChambre extends AuditableEntity implements TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "type_id")
    private Long typeId;

    @TenantId
    @Column(name = "hotel_id", nullable = false, updatable = false)
    private Long hotelId;

    @NotBlank
    @Size(max = 10)
    @Column(name = "type_code", nullable = false, length = 10)
    private String typeCode;

    @NotBlank
    @Size(max = 100)
    @Column(name = "type_nom", nullable = false, length = 100)
    private String typeNom;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "superficie", precision = 6, scale = 2)
    private BigDecimal superficie;

    @NotNull
    @Positive
    @Column(name = "nb_lits_max", nullable = false)
    private Integer nbLitsMax;

    @NotNull
    @Positive
    @Column(name = "nb_personnes_max", nullable = false)
    private Integer nbPersonnesMax;

    @Column(name = "prix_base", precision = 10, scale = 2)
    private BigDecimal prixBase = BigDecimal.ZERO;

    /**
     * Categorie fonctionnelle : CHAMBRE (nuitee) ou SALLE (journee).
     * Tour 49 : permet de gerer les salles de conferences dans le meme
     * modele que les chambres (reservations, nuitees, facturation reutilises).
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "categorie", nullable = false, length = 20)
    private CategorieEspace categorie = CategorieEspace.CHAMBRE;

    @Column(name = "actif", nullable = false)
    private Boolean actif = Boolean.TRUE;

    /** Constructeur JPA. */
    public TypeChambre() {
    }

    public Long getTypeId() {
        return typeId;
    }

    public void setTypeId(Long typeId) {
        this.typeId = typeId;
    }

    @Override
    public Long getHotelId() {
        return hotelId;
    }

    @Override
    public void setHotelId(Long hotelId) {
        this.hotelId = hotelId;
    }

    public String getTypeCode() {
        return typeCode;
    }

    public void setTypeCode(String typeCode) {
        this.typeCode = typeCode;
    }

    public String getTypeNom() {
        return typeNom;
    }

    public void setTypeNom(String typeNom) {
        this.typeNom = typeNom;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getSuperficie() {
        return superficie;
    }

    public void setSuperficie(BigDecimal superficie) {
        this.superficie = superficie;
    }

    public Integer getNbLitsMax() {
        return nbLitsMax;
    }

    public void setNbLitsMax(Integer nbLitsMax) {
        this.nbLitsMax = nbLitsMax;
    }

    public Integer getNbPersonnesMax() {
        return nbPersonnesMax;
    }

    public void setNbPersonnesMax(Integer nbPersonnesMax) {
        this.nbPersonnesMax = nbPersonnesMax;
    }

    public BigDecimal getPrixBase() {
        return prixBase;
    }

    public void setPrixBase(BigDecimal prixBase) {
        this.prixBase = prixBase;
    }

    public Boolean getActif() {
        return actif;
    }

    public void setActif(Boolean actif) {
        this.actif = actif;
    }

    public CategorieEspace getCategorie() {
        return categorie;
    }

    public void setCategorie(CategorieEspace categorie) {
        this.categorie = categorie;
    }
}

package com.cityprojects.citybackend.entity.hebergement;

import com.cityprojects.citybackend.common.audit.AuditableEntity;
import com.cityprojects.citybackend.common.tenant.TenantAware;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Tarification saisonniere d'un type de chambre (haute saison, basse saison,
 * promotion, ...). Permet d'avoir un prix different selon la periode et le
 * week-end.
 *
 * <p>Pas de logique d'application "tarif applicable a une date donnee" dans
 * cette entite : c'est l'affaire du service. Ici on persiste seulement le
 * referentiel.</p>
 */
@Entity
@Table(name = "tarifs_chambres", schema = "hebergement")
public class TarifChambre extends AuditableEntity implements TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tarif_id")
    private Long tarifId;

    @TenantId
    @Column(name = "hotel_id", nullable = false, updatable = false)
    private Long hotelId;

    @NotNull
    @Column(name = "type_id", nullable = false)
    private Long typeId;

    @NotBlank
    @Size(max = 100)
    @Column(name = "nom_tarif", nullable = false, length = 100)
    private String nomTarif;

    @NotNull
    @Column(name = "date_debut", nullable = false)
    private LocalDate dateDebut;

    /** Optionnel : tarif valable jusqu'a nouvel ordre si null. */
    @Column(name = "date_fin")
    private LocalDate dateFin;

    @NotNull
    @DecimalMin(value = "0.0", message = "error.tarifChambre.prixNuit.negative")
    @Column(name = "prix_nuit", nullable = false, precision = 10, scale = 2)
    private BigDecimal prixNuit;

    @DecimalMin(value = "0.0", message = "error.tarifChambre.prixWeekend.negative")
    @Column(name = "prix_weekend", precision = 10, scale = 2)
    private BigDecimal prixWeekend;

    /**
     * Priorite de selection en cas de chevauchement (Tour 44 Phase 1). Plus la
     * valeur est haute, plus le tarif est prioritaire pour une date donnee.
     * Defaut 0 (tarif standard). Permet de gerer "promotion 1er mai" qui ecrase
     * "haute saison" sur la meme journee.
     */
    @Column(name = "priorite", nullable = false)
    private Integer priorite = 0;

    @Column(name = "actif", nullable = false)
    private Boolean actif = Boolean.TRUE;

    /** Constructeur JPA. */
    public TarifChambre() {
    }

    public Long getTarifId() {
        return tarifId;
    }

    public void setTarifId(Long tarifId) {
        this.tarifId = tarifId;
    }

    @Override
    public Long getHotelId() {
        return hotelId;
    }

    @Override
    public void setHotelId(Long hotelId) {
        this.hotelId = hotelId;
    }

    public Long getTypeId() {
        return typeId;
    }

    public void setTypeId(Long typeId) {
        this.typeId = typeId;
    }

    public String getNomTarif() {
        return nomTarif;
    }

    public void setNomTarif(String nomTarif) {
        this.nomTarif = nomTarif;
    }

    public LocalDate getDateDebut() {
        return dateDebut;
    }

    public void setDateDebut(LocalDate dateDebut) {
        this.dateDebut = dateDebut;
    }

    public LocalDate getDateFin() {
        return dateFin;
    }

    public void setDateFin(LocalDate dateFin) {
        this.dateFin = dateFin;
    }

    public BigDecimal getPrixNuit() {
        return prixNuit;
    }

    public void setPrixNuit(BigDecimal prixNuit) {
        this.prixNuit = prixNuit;
    }

    public BigDecimal getPrixWeekend() {
        return prixWeekend;
    }

    public void setPrixWeekend(BigDecimal prixWeekend) {
        this.prixWeekend = prixWeekend;
    }

    public Integer getPriorite() {
        return priorite;
    }

    public void setPriorite(Integer priorite) {
        this.priorite = priorite;
    }

    public Boolean getActif() {
        return actif;
    }

    public void setActif(Boolean actif) {
        this.actif = actif;
    }
}

package com.cityprojects.citybackend.entity.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Ligne d'un bon de commande (un produit + quantite + prix).
 *
 * <p>Pas d'isolation tenant directe ({@code @TenantId}) : la ligne est portee par
 * son {@link BonCommande} parent qui detient {@code hotel_id}. La FK
 * {@code bon_commande_id} (ON DELETE CASCADE) garantit la coherence.</p>
 *
 * <p>Pas d'audit ({@link com.cityprojects.citybackend.common.audit.AuditableEntity})
 * non plus : les modifications sont tracees via le BC parent et les MouvementStock.</p>
 */
@Entity
@Table(name = "lignes_bons_commande", schema = "inventory")
public class LigneBonCommande {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ligne_id")
    private Long ligneId;

    @NotNull
    @Column(name = "bon_commande_id", nullable = false)
    private Long bonCommandeId;

    @NotNull
    @Column(name = "produit_id", nullable = false)
    private Long produitId;

    @NotNull
    @Min(1)
    @Column(name = "quantite_commandee", nullable = false)
    private Integer quantiteCommandee;

    @Min(0)
    @Column(name = "quantite_recue", nullable = false)
    private Integer quantiteRecue = 0;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = true)
    @Column(name = "prix_unitaire", nullable = false, precision = 10, scale = 2)
    private BigDecimal prixUnitaire;

    @Column(name = "date_reception")
    private LocalDate dateReception;

    /** Constructeur JPA. */
    public LigneBonCommande() {
    }

    /** Sous-total : {@code quantiteCommandee * prixUnitaire}. */
    public BigDecimal getSousTotal() {
        if (quantiteCommandee == null || prixUnitaire == null) {
            return BigDecimal.ZERO;
        }
        return prixUnitaire.multiply(BigDecimal.valueOf(quantiteCommandee));
    }

    /** Vrai si {@code quantiteRecue == quantiteCommandee} (ligne soldée). */
    public boolean isCompleteReception() {
        return quantiteRecue != null && quantiteRecue.equals(quantiteCommandee);
    }

    /** Reste a servir : {@code max(0, quantiteCommandee - quantiteRecue)}. */
    public Integer getQuantiteRestante() {
        if (quantiteRecue == null) {
            return quantiteCommandee;
        }
        return Math.max(0, quantiteCommandee - quantiteRecue);
    }

    public Long getLigneId() {
        return ligneId;
    }

    public void setLigneId(Long ligneId) {
        this.ligneId = ligneId;
    }

    public Long getBonCommandeId() {
        return bonCommandeId;
    }

    public void setBonCommandeId(Long bonCommandeId) {
        this.bonCommandeId = bonCommandeId;
    }

    public Long getProduitId() {
        return produitId;
    }

    public void setProduitId(Long produitId) {
        this.produitId = produitId;
    }

    public Integer getQuantiteCommandee() {
        return quantiteCommandee;
    }

    public void setQuantiteCommandee(Integer quantiteCommandee) {
        this.quantiteCommandee = quantiteCommandee;
    }

    public Integer getQuantiteRecue() {
        return quantiteRecue;
    }

    public void setQuantiteRecue(Integer quantiteRecue) {
        this.quantiteRecue = quantiteRecue;
    }

    public BigDecimal getPrixUnitaire() {
        return prixUnitaire;
    }

    public void setPrixUnitaire(BigDecimal prixUnitaire) {
        this.prixUnitaire = prixUnitaire;
    }

    public LocalDate getDateReception() {
        return dateReception;
    }

    public void setDateReception(LocalDate dateReception) {
        this.dateReception = dateReception;
    }
}

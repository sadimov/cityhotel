package com.cityprojects.citybackend.entity.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Ligne d'un bon de sortie (un produit + quantite demandee/servie).
 *
 * <p>Pas d'isolation tenant directe ({@code @TenantId}) : la ligne est portee par
 * son {@link BonSortie} parent. La FK {@code bon_sortie_id} (ON DELETE CASCADE)
 * garantit la coherence.</p>
 */
@Entity
@Table(name = "lignes_bons_sortie", schema = "inventory")
public class LigneBonSortie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ligne_id")
    private Long ligneId;

    @NotNull
    @Column(name = "bon_sortie_id", nullable = false)
    private Long bonSortieId;

    @NotNull
    @Column(name = "produit_id", nullable = false)
    private Long produitId;

    @NotNull
    @Min(1)
    @Column(name = "quantite_demandee", nullable = false)
    private Integer quantiteDemandee;

    @Min(0)
    @Column(name = "quantite_servie", nullable = false)
    private Integer quantiteServie = 0;

    @Column(name = "commentaires", columnDefinition = "TEXT")
    private String commentaires;

    /** Constructeur JPA. */
    public LigneBonSortie() {
    }

    /** Vrai si {@code quantiteServie == quantiteDemandee} (ligne soldée). */
    public boolean isCompleteLivraison() {
        return quantiteServie != null && quantiteServie.equals(quantiteDemandee);
    }

    /** Reste a livrer : {@code max(0, quantiteDemandee - quantiteServie)}. */
    public Integer getQuantiteRestante() {
        if (quantiteServie == null) {
            return quantiteDemandee;
        }
        return Math.max(0, quantiteDemandee - quantiteServie);
    }

    public Long getLigneId() {
        return ligneId;
    }

    public void setLigneId(Long ligneId) {
        this.ligneId = ligneId;
    }

    public Long getBonSortieId() {
        return bonSortieId;
    }

    public void setBonSortieId(Long bonSortieId) {
        this.bonSortieId = bonSortieId;
    }

    public Long getProduitId() {
        return produitId;
    }

    public void setProduitId(Long produitId) {
        this.produitId = produitId;
    }

    public Integer getQuantiteDemandee() {
        return quantiteDemandee;
    }

    public void setQuantiteDemandee(Integer quantiteDemandee) {
        this.quantiteDemandee = quantiteDemandee;
    }

    public Integer getQuantiteServie() {
        return quantiteServie;
    }

    public void setQuantiteServie(Integer quantiteServie) {
        this.quantiteServie = quantiteServie;
    }

    public String getCommentaires() {
        return commentaires;
    }

    public void setCommentaires(String commentaires) {
        this.commentaires = commentaires;
    }
}

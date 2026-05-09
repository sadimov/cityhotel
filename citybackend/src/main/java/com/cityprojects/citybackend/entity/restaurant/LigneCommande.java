package com.cityprojects.citybackend.entity.restaurant;

import com.cityprojects.citybackend.common.audit.AuditableEntity;
import com.cityprojects.citybackend.common.tenant.TenantAware;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.TenantId;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Ligne d'une {@link Commande} POS restaurant (Tour 24).
 *
 * <h3>Snapshot des donnees article</h3>
 * <p>{@code libelle} et {@code prixUnitaire} sont snapshotes au moment de la
 * creation : meme si l'article evolue ensuite (changement de prix, renommage),
 * la ligne reste figee. C'est volontaire pour preserver la cohesion historique
 * (rapports, audits).</p>
 *
 * <h3>Calcul du montant</h3>
 * <p>{@code montant = quantite * prixUnitaire}, recalcule via
 * {@link PrePersist} et {@link PreUpdate}. Source unique de verite : Hibernate
 * (jamais le service a la main, cf. pattern {@code LigneFacture}).</p>
 *
 * <h3>Pas de TVA dans la version POS</h3>
 * <p>Conforme a {@code prompt_restaurant_pos.txt}. Si une TVA doit etre
 * introduite ulterieurement, ajouter {@code tauxTva}, {@code montantTva} et
 * faire un changeset additif.</p>
 */
@Entity
@Table(name = "lignes_commande", schema = "restaurant")
public class LigneCommande extends AuditableEntity implements TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ligne_id")
    private Long ligneId;

    @TenantId
    @Column(name = "hotel_id", nullable = false, updatable = false)
    private Long hotelId;

    @NotNull
    @Column(name = "commande_id", nullable = false)
    private Long commandeId;

    /** FK vers restaurant.articles_menus (Tour 23). Long pour eviter les proxies LAZY. */
    @NotNull
    @Column(name = "article_id", nullable = false)
    private Long articleId;

    /** Snapshot du nom de l'article au moment de la prise de commande. */
    @NotBlank
    @Size(max = 200)
    @Column(name = "libelle", nullable = false, length = 200)
    private String libelle;

    @NotNull
    @DecimalMin(value = "0.01", message = "error.ligneCommande.quantite.positive")
    @Column(name = "quantite", nullable = false, precision = 8, scale = 2)
    private BigDecimal quantite;

    @NotNull
    @DecimalMin(value = "0.0", message = "error.ligneCommande.prix.negative")
    @Column(name = "prix_unitaire", nullable = false, precision = 15, scale = 2)
    private BigDecimal prixUnitaire;

    @NotNull
    @Column(name = "montant", nullable = false, precision = 15, scale = 2)
    private BigDecimal montant = BigDecimal.ZERO;

    @Column(name = "notes_cuisine", columnDefinition = "TEXT")
    private String notesCuisine;

    /** Constructeur JPA. */
    public LigneCommande() {
    }

    /**
     * Recalcule {@code montant = quantite * prixUnitaire} a chaque persist/update.
     * Source unique de verite : Hibernate (cf. ADR Tour 19 sur LigneFacture).
     * Arrondi HALF_UP scale=2.
     */
    @PrePersist
    @PreUpdate
    private void recalcMontant() {
        BigDecimal q = quantite != null ? quantite : BigDecimal.ZERO;
        BigDecimal pu = prixUnitaire != null ? prixUnitaire : BigDecimal.ZERO;
        this.montant = q.multiply(pu).setScale(2, RoundingMode.HALF_UP);
    }

    public Long getLigneId() {
        return ligneId;
    }

    public void setLigneId(Long ligneId) {
        this.ligneId = ligneId;
    }

    @Override
    public Long getHotelId() {
        return hotelId;
    }

    @Override
    public void setHotelId(Long hotelId) {
        this.hotelId = hotelId;
    }

    public Long getCommandeId() {
        return commandeId;
    }

    public void setCommandeId(Long commandeId) {
        this.commandeId = commandeId;
    }

    public Long getArticleId() {
        return articleId;
    }

    public void setArticleId(Long articleId) {
        this.articleId = articleId;
    }

    public String getLibelle() {
        return libelle;
    }

    public void setLibelle(String libelle) {
        this.libelle = libelle;
    }

    public BigDecimal getQuantite() {
        return quantite;
    }

    public void setQuantite(BigDecimal quantite) {
        this.quantite = quantite;
    }

    public BigDecimal getPrixUnitaire() {
        return prixUnitaire;
    }

    public void setPrixUnitaire(BigDecimal prixUnitaire) {
        this.prixUnitaire = prixUnitaire;
    }

    public BigDecimal getMontant() {
        return montant;
    }

    public void setMontant(BigDecimal montant) {
        this.montant = montant;
    }

    public String getNotesCuisine() {
        return notesCuisine;
    }

    public void setNotesCuisine(String notesCuisine) {
        this.notesCuisine = notesCuisine;
    }
}

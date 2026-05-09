package com.cityprojects.citybackend.entity.inventory;

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
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Bon de commande fournisseur (achat).
 *
 * <h3>Numerotation</h3>
 * <p>{@code numero_bc} est genere par hotel via
 * {@link com.cityprojects.citybackend.service.finance.NumerotationService}
 * (type {@link com.cityprojects.citybackend.service.finance.TypeNumerotation#BC}).
 * Format : {@code BC-{exercice}-{codePays}-{6 chiffres}}, ex. {@code BC-2026-MR-000123}.
 * Unicite garantie par hotel via {@code UNIQUE (hotel_id, numero_bc)}.</p>
 *
 * <h3>Cycle de vie</h3>
 * <p>Voir {@link StatutBonCommande}. Le BC est cree {@code BROUILLON}, puis enchaine
 * jusqu'a {@code RECU_COMPLET} ou {@code ANNULE}. La reception (transition vers
 * {@code RECU_PARTIEL}/{@code RECU_COMPLET}) genere des MouvementStock de type ENTREE
 * et incremente {@code Produit.stockActuel}.</p>
 *
 * <h3>References cross-module</h3>
 * <ul>
 *   <li>{@code fournisseurId} : FK vers {@code inventory.fournisseurs}.</li>
 *   <li>{@code userId} : FK vers {@code core.dbusers} (createur du BC).</li>
 *   <li>{@code factureFournisseurId} : FK optionnelle vers {@code finance.factures}
 *       (Tour 19, changeset {@code 008-link-inventory-finance.xml}). {@code null}
 *       tant que la facture fournisseur n'a pas ete recue. Renseignee
 *       manuellement quand l'operateur saisit la facture du fournisseur.</li>
 * </ul>
 *
 * <p>Les lignes sont gerees independamment (cf. {@link LigneBonCommande} +
 * {@code LigneBonCommandeRepository}) - pas de mapping {@code @OneToMany}
 * sur cette entite (eviter les chargements involontaires).</p>
 */
@Entity
@Table(
        name = "bons_commande",
        schema = "inventory",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_bons_commande_hotel_numero",
                columnNames = {"hotel_id", "numero_bc"}))
public class BonCommande extends AuditableEntity implements TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "bon_commande_id")
    private Long bonCommandeId;

    @TenantId
    @Column(name = "hotel_id", nullable = false, updatable = false)
    private Long hotelId;

    /**
     * Genere par NumerotationService (BC-...). @NotNull et non @NotBlank :
     * la valeur est positionnee apres mapper.toEntity() et avant save().
     */
    @NotNull
    @Size(max = 40)
    @Column(name = "numero_bc", nullable = false, length = 40)
    private String numeroBc;

    @NotNull
    @Column(name = "fournisseur_id", nullable = false)
    private Long fournisseurId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 20)
    private StatutBonCommande statut = StatutBonCommande.BROUILLON;

    @NotNull
    @Column(name = "date_commande", nullable = false)
    private LocalDate dateCommande;

    @Column(name = "date_livraison_prevue")
    private LocalDate dateLivraisonPrevue;

    @Column(name = "date_livraison_reelle")
    private LocalDate dateLivraisonReelle;

    @Column(name = "montant_total", precision = 12, scale = 2, nullable = false)
    private BigDecimal montantTotal = BigDecimal.ZERO;

    @Column(name = "montant_tva", precision = 12, scale = 2, nullable = false)
    private BigDecimal montantTva = BigDecimal.ZERO;

    @Column(name = "commentaires", columnDefinition = "TEXT")
    private String commentaires;

    @NotNull
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * FK optionnelle vers {@code finance.factures} (Tour 19). {@code null} tant
     * que la facture fournisseur correspondante n'a pas ete saisie. Stocke en
     * {@code Long} (pas de {@code @ManyToOne}) pour cohesion avec le pattern projet.
     */
    @Column(name = "facture_fournisseur_id")
    private Long factureFournisseurId;

    /** Constructeur JPA - initialise dateCommande a aujourd'hui. */
    public BonCommande() {
        this.dateCommande = LocalDate.now();
    }

    public Long getBonCommandeId() {
        return bonCommandeId;
    }

    public void setBonCommandeId(Long bonCommandeId) {
        this.bonCommandeId = bonCommandeId;
    }

    @Override
    public Long getHotelId() {
        return hotelId;
    }

    @Override
    public void setHotelId(Long hotelId) {
        this.hotelId = hotelId;
    }

    public String getNumeroBc() {
        return numeroBc;
    }

    public void setNumeroBc(String numeroBc) {
        this.numeroBc = numeroBc;
    }

    public Long getFournisseurId() {
        return fournisseurId;
    }

    public void setFournisseurId(Long fournisseurId) {
        this.fournisseurId = fournisseurId;
    }

    public StatutBonCommande getStatut() {
        return statut;
    }

    public void setStatut(StatutBonCommande statut) {
        this.statut = statut;
    }

    public LocalDate getDateCommande() {
        return dateCommande;
    }

    public void setDateCommande(LocalDate dateCommande) {
        this.dateCommande = dateCommande;
    }

    public LocalDate getDateLivraisonPrevue() {
        return dateLivraisonPrevue;
    }

    public void setDateLivraisonPrevue(LocalDate dateLivraisonPrevue) {
        this.dateLivraisonPrevue = dateLivraisonPrevue;
    }

    public LocalDate getDateLivraisonReelle() {
        return dateLivraisonReelle;
    }

    public void setDateLivraisonReelle(LocalDate dateLivraisonReelle) {
        this.dateLivraisonReelle = dateLivraisonReelle;
    }

    public BigDecimal getMontantTotal() {
        return montantTotal;
    }

    public void setMontantTotal(BigDecimal montantTotal) {
        this.montantTotal = montantTotal;
    }

    public BigDecimal getMontantTva() {
        return montantTva;
    }

    public void setMontantTva(BigDecimal montantTva) {
        this.montantTva = montantTva;
    }

    public String getCommentaires() {
        return commentaires;
    }

    public void setCommentaires(String commentaires) {
        this.commentaires = commentaires;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getFactureFournisseurId() {
        return factureFournisseurId;
    }

    public void setFactureFournisseurId(Long factureFournisseurId) {
        this.factureFournisseurId = factureFournisseurId;
    }
}

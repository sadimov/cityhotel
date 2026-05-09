package com.cityprojects.citybackend.entity.finance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * Ligne detaillee d'une {@link Facture}.
 *
 * <p>Pas de {@code hotelId} sur la ligne : l'isolation tenant est portee par la
 * facture parent ({@code factureId}). La coherence cross-tenant est garantie
 * par la FK vers {@code finance.factures} (qui porte le hotel_id) et par les
 * services qui valident l'appartenance via la facture.</p>
 *
 * <h3>Calcul des montants</h3>
 * <p>Recalcule automatiquement par Hibernate via {@link PrePersist} et
 * {@link PreUpdate} (cf. {@link #recalcMontants()}) :
 * <pre>
 *   montantHt  = quantite * prixUnitaire    (arrondi HALF_UP scale=2)
 *   montantTva = montantHt * tauxTva / 100  (arrondi HALF_UP scale=2)
 *   montantTtc = montantHt + montantTva
 * </pre>
 * Source unique de verite : Hibernate. Le service ne fait pas le calcul a la
 * main pour eviter les desyncs.</p>
 *
 * <h3>FK metier (selon {@link TypeLigneFacture})</h3>
 * <ul>
 *   <li>{@code NUITEE} -&gt; {@code nuiteeId} (FK hebergement.nuitees).</li>
 *   <li>{@code PRODUIT} -&gt; {@code produitId} (FK inventory.produits).</li>
 *   <li>{@code COMMANDE} -&gt; {@code commandeId} (FK restaurant - reportee).</li>
 *   <li>{@code SERVICE} -&gt; {@code serviceId} (services hoteliers - reporte).</li>
 *   <li>{@code DIVERS} -&gt; aucune FK.</li>
 * </ul>
 */
@Entity
@Table(name = "lignes_factures", schema = "finance")
public class LigneFacture {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ligne_facture_id")
    private Long ligneFactureId;

    @NotNull
    @Column(name = "facture_id", nullable = false)
    private Long factureId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "type_ligne", nullable = false, length = 20)
    private TypeLigneFacture typeLigne;

    @Column(name = "nuitee_id")
    private Long nuiteeId;

    @Column(name = "produit_id")
    private Long produitId;

    @Column(name = "commande_id")
    private Long commandeId;

    @Column(name = "service_id")
    private Long serviceId;

    @NotNull
    @Size(max = 500)
    @Column(name = "libelle", nullable = false, length = 500)
    private String libelle;

    @NotNull
    @DecimalMin(value = "0.001", message = "error.ligneFacture.quantite.positive")
    @Column(name = "quantite", nullable = false, precision = 10, scale = 3)
    private BigDecimal quantite = BigDecimal.ONE;

    @NotNull
    @DecimalMin(value = "0.0", message = "error.ligneFacture.prix.negative")
    @Column(name = "prix_unitaire", nullable = false, precision = 15, scale = 2)
    private BigDecimal prixUnitaire = BigDecimal.ZERO;

    @NotNull
    @DecimalMin(value = "0.0", message = "error.ligneFacture.tauxTva.negative")
    @Column(name = "taux_tva", nullable = false, precision = 5, scale = 2)
    private BigDecimal tauxTva = BigDecimal.ZERO;

    @NotNull
    @Column(name = "montant_ht", nullable = false, precision = 15, scale = 2)
    private BigDecimal montantHt = BigDecimal.ZERO;

    @NotNull
    @Column(name = "montant_tva", nullable = false, precision = 15, scale = 2)
    private BigDecimal montantTva = BigDecimal.ZERO;

    @NotNull
    @Column(name = "montant_ttc", nullable = false, precision = 15, scale = 2)
    private BigDecimal montantTtc = BigDecimal.ZERO;

    @Column(name = "date_prestation")
    private LocalDate datePrestation;

    /** Constructeur JPA. */
    public LigneFacture() {
    }

    /**
     * Recalcule les montants HT, TVA, TTC. Source unique de verite (cf. ADR
     * Tour 19) : tout calcul cote service est interdit pour eviter les desyncs.
     * Arrondi HALF_UP scale=2 pour les montants finaux.
     */
    @PrePersist
    @PreUpdate
    private void recalcMontants() {
        BigDecimal q = quantite != null ? quantite : BigDecimal.ZERO;
        BigDecimal pu = prixUnitaire != null ? prixUnitaire : BigDecimal.ZERO;
        BigDecimal taux = tauxTva != null ? tauxTva : BigDecimal.ZERO;

        this.montantHt = q.multiply(pu).setScale(2, RoundingMode.HALF_UP);
        this.montantTva = montantHt.multiply(taux)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        this.montantTtc = montantHt.add(montantTva);
    }

    public Long getLigneFactureId() {
        return ligneFactureId;
    }

    public void setLigneFactureId(Long ligneFactureId) {
        this.ligneFactureId = ligneFactureId;
    }

    public Long getFactureId() {
        return factureId;
    }

    public void setFactureId(Long factureId) {
        this.factureId = factureId;
    }

    public TypeLigneFacture getTypeLigne() {
        return typeLigne;
    }

    public void setTypeLigne(TypeLigneFacture typeLigne) {
        this.typeLigne = typeLigne;
    }

    public Long getNuiteeId() {
        return nuiteeId;
    }

    public void setNuiteeId(Long nuiteeId) {
        this.nuiteeId = nuiteeId;
    }

    public Long getProduitId() {
        return produitId;
    }

    public void setProduitId(Long produitId) {
        this.produitId = produitId;
    }

    public Long getCommandeId() {
        return commandeId;
    }

    public void setCommandeId(Long commandeId) {
        this.commandeId = commandeId;
    }

    public Long getServiceId() {
        return serviceId;
    }

    public void setServiceId(Long serviceId) {
        this.serviceId = serviceId;
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

    public BigDecimal getTauxTva() {
        return tauxTva;
    }

    public void setTauxTva(BigDecimal tauxTva) {
        this.tauxTva = tauxTva;
    }

    public BigDecimal getMontantHt() {
        return montantHt;
    }

    public void setMontantHt(BigDecimal montantHt) {
        this.montantHt = montantHt;
    }

    public BigDecimal getMontantTva() {
        return montantTva;
    }

    public void setMontantTva(BigDecimal montantTva) {
        this.montantTva = montantTva;
    }

    public BigDecimal getMontantTtc() {
        return montantTtc;
    }

    public void setMontantTtc(BigDecimal montantTtc) {
        this.montantTtc = montantTtc;
    }

    public LocalDate getDatePrestation() {
        return datePrestation;
    }

    public void setDatePrestation(LocalDate datePrestation) {
        this.datePrestation = datePrestation;
    }
}

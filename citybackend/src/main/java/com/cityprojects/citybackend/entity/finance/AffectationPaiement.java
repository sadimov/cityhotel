package com.cityprojects.citybackend.entity.finance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Pivot N..N entre {@link Paiement} et {@link Facture}.
 *
 * <p>Permet de ventiler un paiement sur plusieurs factures (paiement groupe)
 * et de payer une facture en plusieurs versements.</p>
 *
 * <p>Pas de {@code hotelId} : l'isolation est portee par {@code paiementId}.
 * Coherence inter-tenant garantie par les services (verification que
 * paiement.hotelId == facture.hotelId avant insert).</p>
 *
 * <p>Cle (paiement_id, facture_id) UNIQUE : pas de double affectation possible.</p>
 */
@Entity
@Table(
        name = "affectations_paiements",
        schema = "finance",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_affectations_paiement_facture",
                columnNames = {"paiement_id", "facture_id"}))
public class AffectationPaiement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "affectation_id")
    private Long affectationId;

    @NotNull
    @Column(name = "paiement_id", nullable = false)
    private Long paiementId;

    @NotNull
    @Column(name = "facture_id", nullable = false)
    private Long factureId;

    @NotNull
    @DecimalMin(value = "0.01", message = "error.affectation.montant.positive")
    @Column(name = "montant_affecte", nullable = false, precision = 15, scale = 2)
    private BigDecimal montantAffecte;

    @NotNull
    @Column(name = "date_affectation", nullable = false)
    private Instant dateAffectation = Instant.now();

    /** Constructeur JPA. */
    public AffectationPaiement() {
    }

    public Long getAffectationId() {
        return affectationId;
    }

    public void setAffectationId(Long affectationId) {
        this.affectationId = affectationId;
    }

    public Long getPaiementId() {
        return paiementId;
    }

    public void setPaiementId(Long paiementId) {
        this.paiementId = paiementId;
    }

    public Long getFactureId() {
        return factureId;
    }

    public void setFactureId(Long factureId) {
        this.factureId = factureId;
    }

    public BigDecimal getMontantAffecte() {
        return montantAffecte;
    }

    public void setMontantAffecte(BigDecimal montantAffecte) {
        this.montantAffecte = montantAffecte;
    }

    public Instant getDateAffectation() {
        return dateAffectation;
    }

    public void setDateAffectation(Instant dateAffectation) {
        this.dateAffectation = dateAffectation;
    }
}

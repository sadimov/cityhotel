package com.cityprojects.citybackend.entity.finance;

import com.cityprojects.citybackend.common.tenant.TenantAware;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.TenantId;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Pivot N..N entre {@link Paiement} et {@link Facture} (avec granularite ligne
 * facture optionnelle - Tour 45).
 *
 * <p>Permet de ventiler un paiement sur plusieurs factures (paiement groupe)
 * et de payer une facture en plusieurs versements. Tour 45 introduit la
 * granularite "paiement de lignes selectionnees" via {@link #ligneFactureId}
 * (nullable - legacy = affectation a la facture entiere).</p>
 *
 * <p><b>Isolation multi-tenant native</b> (B1, 2026-05-08) : porte
 * {@code @TenantId hotelId} pour garantir que les agregations
 * ({@code sumMontantByLigneFactureId}, {@code findByPaiementIdOrderBy...},
 * etc.) sont filtrees automatiquement par Hibernate. Le hotelId est
 * positionne automatiquement par Hibernate via le resolver a l'INSERT, et
 * doit toujours coincider avec celui du paiement parent (garantie cote
 * service + trigger PL/pgSQL en Postgres).</p>
 *
 * <p><b>Cle d'unicite</b> (changeset Liquibase {@code 034-add-ligne-facture-id-to-affectations-paiements.xml}) :
 * {@code (paiement_id, facture_id, ligne_facture_id)} avec
 * {@code NULLS NOT DISTINCT} (Postgres 15+) - une affectation a la facture
 * entiere ({@code ligne_facture_id IS NULL}) est unique par (paiement, facture).
 * Sur H2 (profil test), la contrainte unique tombe automatiquement sur les
 * 3 colonnes avec gestion stricte des NULL : 2 NULL sont differents par
 * defaut, mais on contourne en n'utilisant le mode legacy que dans les
 * tests Surefire ou les insertions sont controlees.</p>
 */
@Entity
@Table(
        name = "affectations_paiements",
        schema = "finance",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_affectations_paiement_facture_ligne",
                columnNames = {"paiement_id", "facture_id", "ligne_facture_id"}))
public class AffectationPaiement implements TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "affectation_id")
    private Long affectationId;

    @TenantId
    @Column(name = "hotel_id", nullable = false, updatable = false)
    private Long hotelId;

    @NotNull
    @Column(name = "paiement_id", nullable = false)
    private Long paiementId;

    @NotNull
    @Column(name = "facture_id", nullable = false)
    private Long factureId;

    /**
     * FK optionnelle vers {@code finance.lignes_factures} (Tour 45).
     *
     * <p>Si {@code null} : paiement affecte a la facture <b>entiere</b> (mode
     * legacy, sememantique pre-Tour 45). Si renseigne : paiement affecte a une
     * <b>ligne specifique</b> de la facture (Tour 45 - paiement granulaire de
     * lignes selectionnees, ex. paiement uniquement des nuitees ou uniquement
     * des extras).</p>
     *
     * <p>Cas legacy preserve : les anciennes affectations ({@code ligne_facture_id IS NULL})
     * continuent de fonctionner. Les nouvelles affectations granulaires
     * remplissent la FK pour permettre le suivi reste-a-payer par ligne.</p>
     */
    @Column(name = "ligne_facture_id")
    private Long ligneFactureId;

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

    @Override
    public Long getHotelId() {
        return hotelId;
    }

    @Override
    public void setHotelId(Long hotelId) {
        this.hotelId = hotelId;
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

    public Long getLigneFactureId() {
        return ligneFactureId;
    }

    public void setLigneFactureId(Long ligneFactureId) {
        this.ligneFactureId = ligneFactureId;
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

package com.cityprojects.citybackend.entity.finance;

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
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Paiement encaisse (entree de caisse).
 *
 * <h3>Numerotation</h3>
 * <p>{@code numeroPaiement} est genere par hotel via
 * {@link com.cityprojects.citybackend.service.finance.NumerotationService}
 * (type {@link com.cityprojects.citybackend.service.finance.TypeNumerotation#PAY}).
 * Format : {@code PAY-{exercice}-{codePays}-{6 chiffres}}, ex.
 * {@code PAY-2026-MR-000123}.</p>
 *
 * <h3>Affectation aux factures</h3>
 * <p>Un paiement peut etre affecte a une ou plusieurs factures via
 * {@link AffectationPaiement}. La somme des montants affectes ne peut depasser
 * {@code montantTotal}. Garde-fou implementee dans {@code PaiementService.affecter()}.</p>
 *
 * <h3>Annulation</h3>
 * <p>Un paiement {@code VALIDE} affecte a au moins une facture ne peut etre
 * annule directement : il faut d'abord supprimer les affectations. Un paiement
 * {@code ANNULE} reste en base (audit comptable).</p>
 */
@Entity
@Table(
        name = "paiements",
        schema = "finance",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_paiements_hotel_numero",
                columnNames = {"hotel_id", "numero_paiement"}))
public class Paiement extends AuditableEntity implements TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "paiement_id")
    private Long paiementId;

    @TenantId
    @Column(name = "hotel_id", nullable = false, updatable = false)
    private Long hotelId;

    @NotNull
    @Size(max = 40)
    @Column(name = "numero_paiement", nullable = false, length = 40)
    private String numeroPaiement;

    @Column(name = "compte_id")
    private Long compteId;

    @NotNull
    @DecimalMin(value = "0.01", message = "error.paiement.montant.positive")
    @Column(name = "montant_total", nullable = false, precision = 15, scale = 2)
    private BigDecimal montantTotal;

    @NotNull
    @Size(max = 3)
    @Column(name = "devise", nullable = false, length = 3)
    private String devise = "MRU";

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "mode_paiement", nullable = false, length = 20)
    private ModePaiement modePaiement;

    @Size(max = 100)
    @Column(name = "reference_paiement", length = 100)
    private String referencePaiement;

    @NotNull
    @Column(name = "date_paiement", nullable = false)
    private LocalDate datePaiement;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 20)
    private StatutPaiement statut = StatutPaiement.VALIDE;

    @Column(name = "commentaires", columnDefinition = "TEXT")
    private String commentaires;

    @NotNull
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * Id de l'ecriture comptable CAI/BAN generee a la creation du paiement
     * (Bloc B3). Nullable tant que le paiement n'a pas declenche d'ecriture.
     */
    @Column(name = "ecriture_encaissement_id")
    private Long ecritureEncaissementId;

    /** Constructeur JPA - initialise datePaiement a aujourd'hui. */
    public Paiement() {
        this.datePaiement = LocalDate.now();
    }

    public Long getPaiementId() {
        return paiementId;
    }

    public void setPaiementId(Long paiementId) {
        this.paiementId = paiementId;
    }

    @Override
    public Long getHotelId() {
        return hotelId;
    }

    @Override
    public void setHotelId(Long hotelId) {
        this.hotelId = hotelId;
    }

    public String getNumeroPaiement() {
        return numeroPaiement;
    }

    public void setNumeroPaiement(String numeroPaiement) {
        this.numeroPaiement = numeroPaiement;
    }

    public Long getCompteId() {
        return compteId;
    }

    public void setCompteId(Long compteId) {
        this.compteId = compteId;
    }

    public BigDecimal getMontantTotal() {
        return montantTotal;
    }

    public void setMontantTotal(BigDecimal montantTotal) {
        this.montantTotal = montantTotal;
    }

    public String getDevise() {
        return devise;
    }

    public void setDevise(String devise) {
        this.devise = devise;
    }

    public ModePaiement getModePaiement() {
        return modePaiement;
    }

    public void setModePaiement(ModePaiement modePaiement) {
        this.modePaiement = modePaiement;
    }

    public String getReferencePaiement() {
        return referencePaiement;
    }

    public void setReferencePaiement(String referencePaiement) {
        this.referencePaiement = referencePaiement;
    }

    public LocalDate getDatePaiement() {
        return datePaiement;
    }

    public void setDatePaiement(LocalDate datePaiement) {
        this.datePaiement = datePaiement;
    }

    public StatutPaiement getStatut() {
        return statut;
    }

    public void setStatut(StatutPaiement statut) {
        this.statut = statut;
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

    public Long getEcritureEncaissementId() {
        return ecritureEncaissementId;
    }

    public void setEcritureEncaissementId(Long ecritureEncaissementId) {
        this.ecritureEncaissementId = ecritureEncaissementId;
    }
}

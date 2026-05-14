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
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Facture comptable (client, fournisseur ou avoir).
 *
 * <h3>Numerotation</h3>
 * <p>{@code numeroFacture} est genere par hotel via
 * {@link com.cityprojects.citybackend.service.finance.NumerotationService}
 * (type {@link com.cityprojects.citybackend.service.finance.TypeNumerotation#FACT}
 * pour facture standard et facture fournisseur,
 * {@link com.cityprojects.citybackend.service.finance.TypeNumerotation#AVOIR}
 * pour avoir). Format : {@code FACT-{exercice}-{codePays}-{6 chiffres}}, ex.
 * {@code FACT-2026-MR-000123}. Unicite garantie par hotel via
 * {@code UNIQUE (hotel_id, numero_facture)}.</p>
 *
 * <h3>Cycle de vie</h3>
 * <p>Voir {@link StatutFacture}. Le statut transite uniquement vers l'avant
 * (jamais BROUILLON apres EMISE - audit comptable).</p>
 *
 * <h3>Coherence montants</h3>
 * <p>Au Tour 19, pas de TVA en POS standard ({@code montantTva = 0},
 * {@code montantTtc = montantHt}). Le champ est conserve pour evolution.
 * {@code montantPaye} est mis a jour par {@link AffectationPaiement} : la
 * somme des montants affectes a une facture egale {@code montantPaye}.
 * Garde-fou SQL : {@code montantPaye &lt;= montantTtc + 0.01} (tolerance arrondi).</p>
 *
 * <h3>References cross-module</h3>
 * <ul>
 *   <li>{@code compteId} : FK vers {@code finance.comptes} (optionnel - facture sans compte
 *       pour vente directe / espece).</li>
 *   <li>{@code clientId} : FK vers {@code client.clients} (sera renseigne pour facture client).</li>
 *   <li>{@code societeId} : FK vers {@code client.societes} (B2B).</li>
 *   <li>{@code reservationId} : FK vers {@code hebergement.reservations} (facture
 *       generee depuis une reservation).</li>
 *   <li>{@code fournisseurId} : FK vers {@code inventory.fournisseurs} (facture
 *       fournisseur). Mutuellement exclusif avec reservationId.</li>
 *   <li>{@code factureReferenceId} : FK self vers la facture originale en cas
 *       d'AVOIR.</li>
 * </ul>
 *
 * <p>Convention : toutes les FK sont stockees en {@code Long} (pas de
 * {@code @ManyToOne}) pour eviter les chargements LAZY involontaires et les
 * cycles de serialisation - cf. CLAUDE.md backend.</p>
 */
@Entity
@Table(
        name = "factures",
        schema = "finance",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_factures_hotel_numero",
                columnNames = {"hotel_id", "numero_facture"}))
public class Facture extends AuditableEntity implements TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "facture_id")
    private Long factureId;

    @TenantId
    @Column(name = "hotel_id", nullable = false, updatable = false)
    private Long hotelId;

    /**
     * Genere par NumerotationService (FACT-... ou AVOIR-...). @NotNull et non
     * @NotBlank : la valeur est positionnee apres mapper.toEntity() et avant save().
     */
    @NotNull
    @Size(max = 40)
    @Column(name = "numero_facture", nullable = false, length = 40)
    private String numeroFacture;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "type_facture", nullable = false, length = 20)
    private TypeFacture typeFacture = TypeFacture.FACTURE;

    @Column(name = "compte_id")
    private Long compteId;

    @Column(name = "client_id")
    private Long clientId;

    @Column(name = "societe_id")
    private Long societeId;

    @Column(name = "reservation_id")
    private Long reservationId;

    @Column(name = "fournisseur_id")
    private Long fournisseurId;

    /** Reference vers la facture originale en cas d'AVOIR. */
    @Column(name = "facture_reference_id")
    private Long factureReferenceId;

    @NotNull
    @Column(name = "date_facture", nullable = false)
    private LocalDate dateFacture;

    @Column(name = "date_echeance")
    private LocalDate dateEcheance;

    @NotNull
    @Column(name = "montant_ht", nullable = false, precision = 15, scale = 2)
    private BigDecimal montantHt = BigDecimal.ZERO;

    @NotNull
    @Column(name = "montant_tva", nullable = false, precision = 15, scale = 2)
    private BigDecimal montantTva = BigDecimal.ZERO;

    @NotNull
    @Column(name = "montant_ttc", nullable = false, precision = 15, scale = 2)
    private BigDecimal montantTtc = BigDecimal.ZERO;

    @NotNull
    @Column(name = "montant_paye", nullable = false, precision = 15, scale = 2)
    private BigDecimal montantPaye = BigDecimal.ZERO;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 20)
    private StatutFacture statut = StatutFacture.BROUILLON;

    @NotNull
    @Size(max = 3)
    @Column(name = "devise", nullable = false, length = 3)
    private String devise = "MRU";

    @Column(name = "commentaires", columnDefinition = "TEXT")
    private String commentaires;

    @NotNull
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * Id de l'ecriture comptable VTE generee a l'emission (Bloc B3).
     * Nullable tant que la facture n'a pas ete emise.
     */
    @Column(name = "ecriture_emission_id")
    private Long ecritureEmissionId;

    /** Constructeur JPA - initialise dateFacture a aujourd'hui. */
    public Facture() {
        this.dateFacture = LocalDate.now();
    }

    /**
     * Calcule le montant restant du = montantTtc - montantPaye. Helper pour le
     * service ; pas une colonne stockee (evite les desyncs).
     */
    public BigDecimal getMontantRestant() {
        BigDecimal ttc = montantTtc != null ? montantTtc : BigDecimal.ZERO;
        BigDecimal paye = montantPaye != null ? montantPaye : BigDecimal.ZERO;
        return ttc.subtract(paye);
    }

    public Long getFactureId() {
        return factureId;
    }

    public void setFactureId(Long factureId) {
        this.factureId = factureId;
    }

    @Override
    public Long getHotelId() {
        return hotelId;
    }

    @Override
    public void setHotelId(Long hotelId) {
        this.hotelId = hotelId;
    }

    public String getNumeroFacture() {
        return numeroFacture;
    }

    public void setNumeroFacture(String numeroFacture) {
        this.numeroFacture = numeroFacture;
    }

    public TypeFacture getTypeFacture() {
        return typeFacture;
    }

    public void setTypeFacture(TypeFacture typeFacture) {
        this.typeFacture = typeFacture;
    }

    public Long getCompteId() {
        return compteId;
    }

    public void setCompteId(Long compteId) {
        this.compteId = compteId;
    }

    public Long getClientId() {
        return clientId;
    }

    public void setClientId(Long clientId) {
        this.clientId = clientId;
    }

    public Long getSocieteId() {
        return societeId;
    }

    public void setSocieteId(Long societeId) {
        this.societeId = societeId;
    }

    public Long getReservationId() {
        return reservationId;
    }

    public void setReservationId(Long reservationId) {
        this.reservationId = reservationId;
    }

    public Long getFournisseurId() {
        return fournisseurId;
    }

    public void setFournisseurId(Long fournisseurId) {
        this.fournisseurId = fournisseurId;
    }

    public Long getFactureReferenceId() {
        return factureReferenceId;
    }

    public void setFactureReferenceId(Long factureReferenceId) {
        this.factureReferenceId = factureReferenceId;
    }

    public LocalDate getDateFacture() {
        return dateFacture;
    }

    public void setDateFacture(LocalDate dateFacture) {
        this.dateFacture = dateFacture;
    }

    public LocalDate getDateEcheance() {
        return dateEcheance;
    }

    public void setDateEcheance(LocalDate dateEcheance) {
        this.dateEcheance = dateEcheance;
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

    public BigDecimal getMontantPaye() {
        return montantPaye;
    }

    public void setMontantPaye(BigDecimal montantPaye) {
        this.montantPaye = montantPaye;
    }

    public StatutFacture getStatut() {
        return statut;
    }

    public void setStatut(StatutFacture statut) {
        this.statut = statut;
    }

    public String getDevise() {
        return devise;
    }

    public void setDevise(String devise) {
        this.devise = devise;
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

    public Long getEcritureEmissionId() {
        return ecritureEmissionId;
    }

    public void setEcritureEmissionId(Long ecritureEmissionId) {
        this.ecritureEmissionId = ecritureEmissionId;
    }
}

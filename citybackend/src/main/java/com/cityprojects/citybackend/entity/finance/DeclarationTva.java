package com.cityprojects.citybackend.entity.finance;

import com.cityprojects.citybackend.common.audit.AuditableEntity;
import com.cityprojects.citybackend.common.tenant.TenantAware;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Déclaration TVA pour une période donnée (B4).
 *
 * <p>Périodicité standard mauritanienne : mensuelle (déclaration le 15 du
 * mois suivant pour la majorité des assujettis). Le service applicatif ne
 * contraint pas la longueur de la période : un admin peut produire une
 * déclaration ponctuelle (semaine, trimestre) si besoin.</p>
 *
 * <h3>Régime de liquidation</h3>
 * <pre>
 *   totalTvaCollectee   = somme des CREDIT 445700 sur la periode
 *   totalTvaDeductible  = somme des DEBIT 445600 sur la periode
 *   totalTvaADecaisser  = totalTvaCollectee - totalTvaDeductible
 *                         (négatif = crédit reportable)
 * </pre>
 *
 * <p>À la validation : une écriture comptable de liquidation est générée
 * (journal OD) qui solde 445700/445600 sur la période et matérialise le
 * solde sur 445800. Cf. {@code DeclarationTvaServiceImpl#valider}.</p>
 *
 * <h3>Unicité</h3>
 * <p>{@code (hotel_id, date_debut, date_fin)} : pas deux déclarations
 * couvrant exactement la même période - garantit l'idempotence du POST.</p>
 */
@Entity
@Table(
        name = "declaration_tva",
        schema = "finance",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_declaration_tva_hotel_periode",
                columnNames = {"hotel_id", "date_debut", "date_fin"}))
public class DeclarationTva extends AuditableEntity implements TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @TenantId
    @Column(name = "hotel_id", nullable = false, updatable = false)
    private Long hotelId;

    @NotNull
    @Column(name = "date_debut", nullable = false)
    private LocalDate dateDebut;

    @NotNull
    @Column(name = "date_fin", nullable = false)
    private LocalDate dateFin;

    @NotNull
    @Column(name = "total_tva_collectee", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalTvaCollectee = BigDecimal.ZERO;

    @NotNull
    @Column(name = "total_tva_deductible", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalTvaDeductible = BigDecimal.ZERO;

    /**
     * Solde de liquidation : positif = à décaisser (CRÉDIT 445800), négatif
     * = crédit reportable (DÉBIT 445800).
     */
    @NotNull
    @Column(name = "total_tva_a_decaisser", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalTvaADecaisser = BigDecimal.ZERO;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 20)
    private StatutDeclarationTva statut = StatutDeclarationTva.BROUILLON;

    /** Exercice de rattachement (résolu via la date_debut). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exercice_id")
    private Exercice exercice;

    /**
     * Id de l'écriture de liquidation générée à la validation. Null tant
     * que le statut est BROUILLON.
     */
    @Column(name = "ecriture_liquidation_id")
    private Long ecritureLiquidationId;

    @Column(name = "date_validation")
    private LocalDate dateValidation;

    @Size(max = 100)
    @Column(name = "validee_par", length = 100)
    private String valideePar;

    /** Constructeur JPA. */
    public DeclarationTva() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    @Override
    public Long getHotelId() {
        return hotelId;
    }

    @Override
    public void setHotelId(Long hotelId) {
        this.hotelId = hotelId;
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

    public BigDecimal getTotalTvaCollectee() {
        return totalTvaCollectee;
    }

    public void setTotalTvaCollectee(BigDecimal totalTvaCollectee) {
        this.totalTvaCollectee = totalTvaCollectee;
    }

    public BigDecimal getTotalTvaDeductible() {
        return totalTvaDeductible;
    }

    public void setTotalTvaDeductible(BigDecimal totalTvaDeductible) {
        this.totalTvaDeductible = totalTvaDeductible;
    }

    public BigDecimal getTotalTvaADecaisser() {
        return totalTvaADecaisser;
    }

    public void setTotalTvaADecaisser(BigDecimal totalTvaADecaisser) {
        this.totalTvaADecaisser = totalTvaADecaisser;
    }

    public StatutDeclarationTva getStatut() {
        return statut;
    }

    public void setStatut(StatutDeclarationTva statut) {
        this.statut = statut;
    }

    public Exercice getExercice() {
        return exercice;
    }

    public void setExercice(Exercice exercice) {
        this.exercice = exercice;
    }

    public Long getEcritureLiquidationId() {
        return ecritureLiquidationId;
    }

    public void setEcritureLiquidationId(Long ecritureLiquidationId) {
        this.ecritureLiquidationId = ecritureLiquidationId;
    }

    public LocalDate getDateValidation() {
        return dateValidation;
    }

    public void setDateValidation(LocalDate dateValidation) {
        this.dateValidation = dateValidation;
    }

    public String getValideePar() {
        return valideePar;
    }

    public void setValideePar(String valideePar) {
        this.valideePar = valideePar;
    }
}

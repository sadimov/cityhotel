package com.cityprojects.citybackend.entity.finance;

import com.cityprojects.citybackend.common.tenant.TenantAware;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.TenantId;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * <h2>⚠️ DÉPRÉCATION SÉMANTIQUE — Tour 20 (2026-05-07)</h2>
 *
 * <p>Cette entité est un <b>journal de mouvements sur compte AUXILIAIRE CLIENT</b>
 * (DEBIT/CREDIT sur le solde de la dette client), <b>PAS</b> une écriture
 * comptable partie double SYSCOHADA. Le nom {@code OperationCompte} prête à
 * confusion : lire comme {@code MouvementCompteClient}.</p>
 *
 * <p><b>Limites structurelles vs SYSCOHADA</b> (cf. audit Tour 20) :</p>
 * <ul>
 *   <li>Une ligne au lieu de N≥2 (pas de partie double équilibrée).</li>
 *   <li>Pas de notion de journal (caisse/banque/ventes/achats).</li>
 *   <li>Pas de classes 1-9 (le compte référencé est un compte AUXILIAIRE,
 *       pas un compte du PCG).</li>
 *   <li>Pas d'exercice clôturé.</li>
 * </ul>
 *
 * <p>La <b>comptabilité générale</b> est <b>externalisée vers Dolibarr</b> via
 * bridge Feign REST (cf. {@code CLAUDE.md} racine §6.2). Cette entité reste
 * utile pour l'audit trail interne du solde client, mais ne couvre pas les
 * obligations comptables réglementaires (Article 14 OHADA).</p>
 *
 * <p>Renommage prévu : {@code OperationCompte} → {@code MouvementCompteClient}.</p>
 *
 * <h2>Rôle effectif</h2>
 *
 * <p>Journal d'audit des mouvements sur les comptes auxiliaires client/société.</p>
 *
 * <p>Chaque modification de {@code Compte.soldeActuel} doit creer une
 * {@link OperationCompte} (DEBIT ou CREDIT). C'est l'audit trail
 * <b>auxiliaire client</b> : une lecture chronologique des operations doit
 * reconstituer le solde a tout instant.</p>
 *
 * <h3>Contraintes</h3>
 * <ul>
 *   <li>{@code montant} toujours positif (le sens est donne par
 *       {@link TypeOperationCompte}).</li>
 *   <li>{@code soldeAvant} et {@code soldeApres} permettent de detecter les
 *       incoherences (soldeApres = soldeAvant +/- montant).</li>
 *   <li>Pas d'audit (createdAt/updatedBy) : la table est elle-meme un audit
 *       trail. {@code dateOperation} suffit.</li>
 * </ul>
 *
 * <h3>References</h3>
 * <ul>
 *   <li>{@code factureId} : pour DEBIT issu d'une facture (optionnel).</li>
 *   <li>{@code paiementId} : pour CREDIT issu d'un paiement (optionnel).</li>
 * </ul>
 */
@Deprecated(since = "2026-05-07", forRemoval = false)
@Entity
@Table(name = "operations_comptes", schema = "finance")
public class OperationCompte implements TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "operation_id")
    private Long operationId;

    @TenantId
    @Column(name = "hotel_id", nullable = false, updatable = false)
    private Long hotelId;

    @NotNull
    @Column(name = "compte_id", nullable = false)
    private Long compteId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "type_operation", nullable = false, length = 20)
    private TypeOperationCompte typeOperation;

    @NotNull
    @DecimalMin(value = "0.01", message = "error.operation.montant.positive")
    @Column(name = "montant", nullable = false, precision = 15, scale = 2)
    private BigDecimal montant;

    @NotNull
    @Size(max = 500)
    @Column(name = "libelle", nullable = false, length = 500)
    private String libelle;

    @Column(name = "facture_id")
    private Long factureId;

    @Column(name = "paiement_id")
    private Long paiementId;

    @NotNull
    @Column(name = "solde_avant", nullable = false, precision = 15, scale = 2)
    private BigDecimal soldeAvant;

    @NotNull
    @Column(name = "solde_apres", nullable = false, precision = 15, scale = 2)
    private BigDecimal soldeApres;

    @NotNull
    @Column(name = "date_operation", nullable = false)
    private Instant dateOperation = Instant.now();

    @NotNull
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Constructeur JPA. */
    public OperationCompte() {
    }

    public Long getOperationId() {
        return operationId;
    }

    public void setOperationId(Long operationId) {
        this.operationId = operationId;
    }

    @Override
    public Long getHotelId() {
        return hotelId;
    }

    @Override
    public void setHotelId(Long hotelId) {
        this.hotelId = hotelId;
    }

    public Long getCompteId() {
        return compteId;
    }

    public void setCompteId(Long compteId) {
        this.compteId = compteId;
    }

    public TypeOperationCompte getTypeOperation() {
        return typeOperation;
    }

    public void setTypeOperation(TypeOperationCompte typeOperation) {
        this.typeOperation = typeOperation;
    }

    public BigDecimal getMontant() {
        return montant;
    }

    public void setMontant(BigDecimal montant) {
        this.montant = montant;
    }

    public String getLibelle() {
        return libelle;
    }

    public void setLibelle(String libelle) {
        this.libelle = libelle;
    }

    public Long getFactureId() {
        return factureId;
    }

    public void setFactureId(Long factureId) {
        this.factureId = factureId;
    }

    public Long getPaiementId() {
        return paiementId;
    }

    public void setPaiementId(Long paiementId) {
        this.paiementId = paiementId;
    }

    public BigDecimal getSoldeAvant() {
        return soldeAvant;
    }

    public void setSoldeAvant(BigDecimal soldeAvant) {
        this.soldeAvant = soldeAvant;
    }

    public BigDecimal getSoldeApres() {
        return soldeApres;
    }

    public void setSoldeApres(BigDecimal soldeApres) {
        this.soldeApres = soldeApres;
    }

    public Instant getDateOperation() {
        return dateOperation;
    }

    public void setDateOperation(Instant dateOperation) {
        this.dateOperation = dateOperation;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }
}

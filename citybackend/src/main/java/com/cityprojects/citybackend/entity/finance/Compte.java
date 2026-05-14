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

/**
 * <h2>Compte AUXILIAIRE CLIENT / SOCIETE</h2>
 *
 * <p>Cette entite represente un compte <b>AUXILIAIRE</b> (suivi du solde
 * client/societe par tenant), composante du grand-livre tenu nativement par
 * l'application depuis B1 (2026-05-08).</p>
 *
 * <p>Distinction avec le Plan Comptable General : un {@code Compte} est
 * un <em>auxiliaire</em> (tiers individualise) qui se rattache via mapping
 * comptable a un compte collectif du PCG :
 * <ul>
 *   <li>par defaut, {@code CLIENT_PARTICULIER} -> {@code 411100},
 *       {@code CLIENT_SOCIETE} -> {@code 411200} (cf. {@link TypeEvenementComptable}).</li>
 *   <li>la balance auxiliaire client de cet hotel doit toujours egaler le
 *       solde du compte collectif au PCG (controle de coherence Tour B5).</li>
 * </ul>
 *
 * <p>Le solde {@code soldeActuel} est mis a jour de facon synchrone par
 * {@link OperationCompte} a chaque facturation (DEBIT) ou paiement (CREDIT).
 * Le champ est la source de verite ; les operations fournissent l'audit trail
 * auxiliaire client.</p>
 *
 * <h3>Numerotation</h3>
 * <p>{@code numeroCompte} suit le format simplifie {@code CPT-{type}-{client/societe-id}}
 * (genere par le service, pas via NumerotationService). Exemples :
 * {@code CPT-CLI-12345}, {@code CPT-SOC-9876}.</p>
 *
 * <h3>References cross-module</h3>
 * <ul>
 *   <li>{@code clientId} : FK vers {@code client.clients} (si {@code typeCompte = CLIENT}).</li>
 *   <li>{@code societeId} : FK vers {@code client.societes} (si {@code typeCompte = SOCIETE}).</li>
 *   <li>Mutuellement exclusifs (regle service-side).</li>
 * </ul>
 */
@Entity
@Table(
        name = "comptes",
        schema = "finance",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_comptes_hotel_numero",
                columnNames = {"hotel_id", "numero_compte"}))
public class Compte extends AuditableEntity implements TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "compte_id")
    private Long compteId;

    @TenantId
    @Column(name = "hotel_id", nullable = false, updatable = false)
    private Long hotelId;

    @NotNull
    @Size(max = 40)
    @Column(name = "numero_compte", nullable = false, length = 40)
    private String numeroCompte;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "type_compte", nullable = false, length = 20)
    private TypeCompte typeCompte;

    @Column(name = "client_id")
    private Long clientId;

    @Column(name = "societe_id")
    private Long societeId;

    @NotNull
    @Column(name = "solde_actuel", nullable = false, precision = 15, scale = 2)
    private BigDecimal soldeActuel = BigDecimal.ZERO;

    @NotNull
    @Column(name = "credit_limite", nullable = false, precision = 15, scale = 2)
    private BigDecimal creditLimite = BigDecimal.ZERO;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 20)
    private StatutCompte statut = StatutCompte.ACTIF;

    /** Constructeur JPA. */
    public Compte() {
    }

    public Long getCompteId() {
        return compteId;
    }

    public void setCompteId(Long compteId) {
        this.compteId = compteId;
    }

    @Override
    public Long getHotelId() {
        return hotelId;
    }

    @Override
    public void setHotelId(Long hotelId) {
        this.hotelId = hotelId;
    }

    public String getNumeroCompte() {
        return numeroCompte;
    }

    public void setNumeroCompte(String numeroCompte) {
        this.numeroCompte = numeroCompte;
    }

    public TypeCompte getTypeCompte() {
        return typeCompte;
    }

    public void setTypeCompte(TypeCompte typeCompte) {
        this.typeCompte = typeCompte;
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

    public BigDecimal getSoldeActuel() {
        return soldeActuel;
    }

    public void setSoldeActuel(BigDecimal soldeActuel) {
        this.soldeActuel = soldeActuel;
    }

    public BigDecimal getCreditLimite() {
        return creditLimite;
    }

    public void setCreditLimite(BigDecimal creditLimite) {
        this.creditLimite = creditLimite;
    }

    public StatutCompte getStatut() {
        return statut;
    }

    public void setStatut(StatutCompte statut) {
        this.statut = statut;
    }
}

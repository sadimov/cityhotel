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
 * <h2>⚠️ DÉPRÉCATION SÉMANTIQUE — Tour 20 (2026-05-07)</h2>
 *
 * <p>Cette entité représente un compte <b>AUXILIAIRE CLIENT/SOCIETE</b>
 * (suivi de la dette client par tenant), <b>PAS</b> un compte du Plan Comptable
 * Général SYSCOHADA / mauritanien. Le nom {@code Compte} prête à confusion :
 * lire comme {@code CompteClient} ou {@code CompteAuxiliaire}.</p>
 *
 * <p>La <b>comptabilité générale</b> (partie double, classes 1-9, balances, bilan,
 * compte de résultat, journaux, FEC, exercices clôturés) est <b>externalisée
 * vers Dolibarr</b> via bridge Feign REST (cf. {@code CLAUDE.md} racine §6.2 +
 * audit Tour 20 : 5 🔴 + 6 🟠 + 5 💡).</p>
 *
 * <p>Renommage prévu lors d'un tour de cleanup ultérieur :
 * {@code Compte} → {@code CompteClient}, {@code OperationCompte} →
 * {@code MouvementCompteClient}. {@code @Deprecated(forRemoval = false)} :
 * la fonction reste utile (auxiliaire client), seul le nom change.</p>
 *
 * <h2>Rôle effectif (auxiliaire client uniquement)</h2>
 *
 * <p>Compte client/societe (debit/credit) pour facturation differee.</p>
 *
 * <p>Permet de suivre le solde du d'un client ou d'une societe sur l'hotel.
 * Le solde est mis a jour de facon synchrone par {@link OperationCompte} a
 * chaque facturation (DEBIT) ou paiement (CREDIT). Le champ {@code soldeActuel}
 * est la source de verite ; les operations fournissent l'audit trail
 * <b>auxiliaire client</b> (pas une partie double SYSCOHADA).</p>
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
@Deprecated(since = "2026-05-07", forRemoval = false)
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

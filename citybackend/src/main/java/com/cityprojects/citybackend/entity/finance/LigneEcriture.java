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
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.TenantId;

import java.math.BigDecimal;

/**
 * Ligne d'ecriture comptable (debit ou credit).
 *
 * <p>Une ligne reference :</p>
 * <ul>
 *   <li>un compte du PCG ({@code compteCode}, FK logique vers
 *       {@code finance.plan_comptable_general}). La validation que le compte
 *       existe et est {@code utilisable = true} est effectuee cote service
 *       (pas de FK SQL puisque PCG est une reference globale tandis que
 *       l'ecriture est tenant-scopee).</li>
 *   <li>un sens ({@link SensLigne#DEBIT} ou {@link SensLigne#CREDIT}),</li>
 *   <li>un montant strictement positif (le sens porte l'information de
 *       direction - les montants negatifs sont interdits).</li>
 * </ul>
 *
 * <h3>Compte auxiliaire</h3>
 * <p>{@code compteAuxiliaireRef} est une chaine optionnelle qui complete le
 * code compte par une reference auxiliaire (ex. {@code "411100/CLI-2026-MR-000123"}
 * pour identifier le client particulier dans le compte 411100). Pas de
 * controle structurel - convention applicative.</p>
 *
 * <h3>Tenant-aware</h3>
 * <p>L'isolation par hotel est portee par {@link org.hibernate.annotations.TenantId}.
 * Une requete cross-tenant via JPQL/Criteria sera filtree automatiquement.
 * Le {@code hotel_id} est populate par Hibernate au moment de l'INSERT
 * via le {@code CityTenantIdentifierResolver}.</p>
 */
@Entity
@Table(name = "ligne_ecriture", schema = "finance")
public class LigneEcriture extends AuditableEntity implements TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @TenantId
    @Column(name = "hotel_id", nullable = false, updatable = false)
    private Long hotelId;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ecriture_id", nullable = false)
    private EcritureComptable ecriture;

    /**
     * Ordre d'affichage de la ligne dans l'ecriture (1, 2, 3...). Pas de
     * contrainte d'unicite au sein d'une ecriture (deux lignes peuvent
     * partager le meme ordre - le tri secondaire est par id).
     */
    @Column(name = "ordre", nullable = false)
    private int ordre;

    @NotNull
    @Size(min = 1, max = 10)
    @Column(name = "compte_code", nullable = false, length = 10)
    private String compteCode;

    /**
     * Libelle de la ligne. Peut differer du libelle de l'ecriture parente
     * (ex. ecriture libelle="Vente Facture FACT-2026-MR-000001", ligne
     * libelle="Vente nuitee chambre 12"). Optionnel - si null, le client
     * affichera le libelle de l'ecriture parente.
     */
    @Size(max = 500)
    @Column(name = "libelle", length = 500)
    private String libelle;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "sens", nullable = false, length = 10)
    private SensLigne sens;

    @NotNull
    @DecimalMin(value = "0.01", message = "{error.ligneEcriture.montantPositif}")
    @Column(name = "montant", nullable = false, precision = 19, scale = 2)
    private BigDecimal montant;

    /**
     * Reference auxiliaire facultative (ex. code client/fournisseur). Permet
     * d'identifier la contre-partie d'un compte collectif (411100 - clients
     * particuliers). Pas de FK SQL - convention applicative.
     */
    @Size(max = 50)
    @Column(name = "compte_auxiliaire_ref", length = 50)
    private String compteAuxiliaireRef;

    /** Constructeur JPA. */
    public LigneEcriture() {
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

    public EcritureComptable getEcriture() {
        return ecriture;
    }

    public void setEcriture(EcritureComptable ecriture) {
        this.ecriture = ecriture;
    }

    public int getOrdre() {
        return ordre;
    }

    public void setOrdre(int ordre) {
        this.ordre = ordre;
    }

    public String getCompteCode() {
        return compteCode;
    }

    public void setCompteCode(String compteCode) {
        this.compteCode = compteCode;
    }

    public String getLibelle() {
        return libelle;
    }

    public void setLibelle(String libelle) {
        this.libelle = libelle;
    }

    public SensLigne getSens() {
        return sens;
    }

    public void setSens(SensLigne sens) {
        this.sens = sens;
    }

    public BigDecimal getMontant() {
        return montant;
    }

    public void setMontant(BigDecimal montant) {
        this.montant = montant;
    }

    public String getCompteAuxiliaireRef() {
        return compteAuxiliaireRef;
    }

    public void setCompteAuxiliaireRef(String compteAuxiliaireRef) {
        this.compteAuxiliaireRef = compteAuxiliaireRef;
    }
}

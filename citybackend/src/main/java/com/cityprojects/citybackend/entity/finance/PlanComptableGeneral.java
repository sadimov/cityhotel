package com.cityprojects.citybackend.entity.finance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Référentiel du Plan Comptable Général mauritanien (SYSCOHADA).
 *
 * <h2>Caractéristiques structurelles</h2>
 * <ul>
 *   <li><b>Global, pas tenant</b> : aucune annotation {@code @TenantId}, pas
 *       d'héritage de {@code AuditableEntity}. Le PCG est partagé entre tous les
 *       hôtels (référentiel réglementaire commun).</li>
 *   <li><b>Lecture seule en production</b> : seed Liquibase au démarrage,
 *       aucun endpoint REST d'écriture (POST/PUT/DELETE absents
 *       de {@code PlanComptableController}). Les évolutions du PCG passent par
 *       de nouveaux changesets Liquibase.</li>
 *   <li><b>Clé primaire métier</b> : {@code compteCode} (string 10 chars) est
 *       la PK. Pas de PK technique - le code comptable est la référence
 *       universelle (ex. {@code "411100"}).</li>
 * </ul>
 *
 * <h2>Hiérarchie</h2>
 * <p>Les comptes "racine" ({@code utilisable = false}) servent de regroupements
 * pour la balance générale et le bilan. Exemples : {@code 100000} (Capitaux),
 * {@code 411000} (Clients), {@code 700000} (Produits). Les comptes feuilles
 * ({@code utilisable = true}) sont ceux référencés par les écritures.</p>
 *
 * <p>La hiérarchie est exprimée via {@code parentCode} (FK logique vers
 * {@code compteCode}) : un compte de niveau 4 a comme parent un compte de
 * niveau 3, etc.</p>
 */
@Entity
@Table(name = "plan_comptable_general", schema = "finance")
public class PlanComptableGeneral {

    /** Code comptable - PK métier (ex: {@code "411100"}, {@code "531401"}). */
    @Id
    @NotNull
    @Size(min = 1, max = 10)
    @Column(name = "compte_code", length = 10)
    private String compteCode;

    @NotNull
    @Size(max = 255)
    @Column(name = "libelle", nullable = false, length = 255)
    private String libelle;

    /** Classe SYSCOHADA (1-7, parfois 8-9 - on borne ici 1-7 strict). */
    @NotNull
    @Min(1)
    @Max(7)
    @Column(name = "classe", nullable = false)
    private Integer classe;

    /** Code du compte parent dans la hiérarchie (nullable pour la racine). */
    @Size(max = 10)
    @Column(name = "parent_code", length = 10)
    private String parentCode;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "nature", nullable = false, length = 20)
    private NatureCompte nature;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "sens_normal", nullable = false, length = 20)
    private SensNormal sensNormal;

    /**
     * {@code true} : compte de mouvement (peut être référencé par une écriture).
     * {@code false} : compte de regroupement / titre (utilisé pour la balance
     * et le bilan mais jamais référencé par une écriture).
     */
    @NotNull
    @Column(name = "utilisable", nullable = false)
    private Boolean utilisable;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 20)
    private StatutCompteComptable statut = StatutCompteComptable.ACTIF;

    /** Constructeur JPA. */
    public PlanComptableGeneral() {
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

    public Integer getClasse() {
        return classe;
    }

    public void setClasse(Integer classe) {
        this.classe = classe;
    }

    public String getParentCode() {
        return parentCode;
    }

    public void setParentCode(String parentCode) {
        this.parentCode = parentCode;
    }

    public NatureCompte getNature() {
        return nature;
    }

    public void setNature(NatureCompte nature) {
        this.nature = nature;
    }

    public SensNormal getSensNormal() {
        return sensNormal;
    }

    public void setSensNormal(SensNormal sensNormal) {
        this.sensNormal = sensNormal;
    }

    public Boolean getUtilisable() {
        return utilisable;
    }

    public void setUtilisable(Boolean utilisable) {
        this.utilisable = utilisable;
    }

    public StatutCompteComptable getStatut() {
        return statut;
    }

    public void setStatut(StatutCompteComptable statut) {
        this.statut = statut;
    }
}

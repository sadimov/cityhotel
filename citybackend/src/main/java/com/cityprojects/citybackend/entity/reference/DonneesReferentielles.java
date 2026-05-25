package com.cityprojects.citybackend.entity.reference;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Référentiel multilingue (nationalités, types d'identification, etc.).
 *
 * <h2>Caractéristiques</h2>
 * <ul>
 *   <li><b>Global, pas tenant</b> : aucune annotation {@code @TenantId}, pas
 *       d'héritage de {@code AuditableEntity}. Le référentiel est partagé entre
 *       tous les hôtels.</li>
 *   <li><b>Lecture seule en production</b> : seed Liquibase, aucun endpoint
 *       REST d'écriture. Évolutions via nouveaux changesets Liquibase.</li>
 *   <li><b>Clé technique</b> : {@code ref_id BIGSERIAL}. Le {@code code} (ISO
 *       3166-1 alpha-2 pour les nationalités) sert d'identifiant métier mais
 *       n'est pas la PK.</li>
 * </ul>
 *
 * <p>Catégories utilisées :
 * <ul>
 *   <li>{@code nationalite} — pays / nationalité du client (seed 061)</li>
 *   <li>{@code type_identification} — CNI, passeport, etc. (à seeder)</li>
 * </ul>
 */
@Entity
@Table(name = "donnees_referentielles", schema = "core")
public class DonneesReferentielles {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ref_id")
    private Long refId;

    @NotBlank
    @Size(max = 50)
    @Column(name = "categorie", nullable = false, length = 50)
    private String categorie;

    @Size(max = 20)
    @Column(name = "code", length = 20)
    private String code;

    @NotBlank
    @Size(max = 200)
    @Column(name = "libelle", nullable = false, length = 200)
    private String libelle;

    @Size(max = 200)
    @Column(name = "libelle_en", length = 200)
    private String libelleEn;

    @Size(max = 200)
    @Column(name = "libelle_ar", length = 200)
    private String libelleAr;

    @Column(name = "ordre_affichage")
    private Integer ordreAffichage;

    @Column(name = "actif")
    private Boolean actif;

    public Long getRefId() { return refId; }
    public void setRefId(Long refId) { this.refId = refId; }

    public String getCategorie() { return categorie; }
    public void setCategorie(String categorie) { this.categorie = categorie; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getLibelle() { return libelle; }
    public void setLibelle(String libelle) { this.libelle = libelle; }

    public String getLibelleEn() { return libelleEn; }
    public void setLibelleEn(String libelleEn) { this.libelleEn = libelleEn; }

    public String getLibelleAr() { return libelleAr; }
    public void setLibelleAr(String libelleAr) { this.libelleAr = libelleAr; }

    public Integer getOrdreAffichage() { return ordreAffichage; }
    public void setOrdreAffichage(Integer ordreAffichage) { this.ordreAffichage = ordreAffichage; }

    public Boolean getActif() { return actif; }
    public void setActif(Boolean actif) { this.actif = actif; }
}

package com.cityprojects.citybackend.entity.core;

import com.cityprojects.citybackend.common.audit.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Parametre applicatif global (configuration cross-tenant).
 *
 * <h2>Pourquoi non-tenant</h2>
 * <p>Cette entite n'a <b>pas</b> de {@code hotelId} et n'implemente <b>pas</b>
 * {@link com.cityprojects.citybackend.common.tenant.TenantAware}. Les
 * parametres portes par cette table sont <b>globaux a l'application</b> :
 * fuseau horaire serveur, devise par defaut, message de maintenance,
 * reglages de retention historique, parametres de notification systeme...
 * Ils sont gerees uniquement par les SUPERADMIN et s'appliquent a tous
 * les tenants.</p>
 *
 * <h2>Restriction "modifiable"</h2>
 * <p>Le champ {@link #modifiable} permet de proteger les parametres
 * <i>systeme</i> (timezone, devise, ...) : un parametre {@code modifiable=false}
 * ne peut etre ni modifie ni supprime via l'API REST. Seul un changement
 * direct en base (ou un nouveau changeset Liquibase) peut le mettre a jour.
 * Les parametres modifiables (notification email, retention, ...) sont
 * editables par les SUPERADMIN via l'API.</p>
 *
 * <h2>Categorie</h2>
 * <p>Champ libre permettant de regrouper les parametres dans l'IHM admin
 * (ex. {@code "system"}, {@code "notification"}, {@code "audit"},
 * {@code "maintenance"}). Optionnel.</p>
 *
 * <h2>AuditableEntity</h2>
 * <p>Conserve {@code created_at}, {@code updated_at}, {@code created_by},
 * {@code updated_by} pour la tracabilite des modifications de configuration.
 * Particulierement important pour les parametres systeme.</p>
 */
@Entity
@Table(name = "parametres", schema = "core")
public class Parametre extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "parametre_id")
    private Long parametreId;

    /**
     * Cle technique unique (insensible a la casse au niveau service via
     * {@code findByCleIgnoreCase}). Format conventionnel : {@code module.sous-module.nom}
     * (ex. {@code app.timezone}, {@code notification.email.from}).
     * Limite a 80 caracteres pour rester compatible avec les index Postgres.
     */
    @Column(name = "cle", unique = true, nullable = false, length = 80)
    @NotBlank(message = "error.parametre.cle.blank")
    @Size(max = 80, message = "error.parametre.cle.tooLong")
    private String cle;

    /**
     * Valeur du parametre, stockee en TEXT (suffisamment large pour
     * accomoder messages multi-lignes, JSON, listes).
     */
    @Column(name = "valeur", columnDefinition = "TEXT")
    private String valeur;

    /**
     * Description courte (visible dans l'IHM admin pour expliquer le role
     * du parametre). Optionnel mais fortement recommande.
     */
    @Column(name = "description", length = 500)
    @Size(max = 500, message = "error.parametre.description.tooLong")
    private String description;

    /**
     * {@code true} (defaut) : parametre editable via l'API admin.
     * {@code false} : parametre systeme protege (modification refusee
     * par {@code BusinessException("error.parametre.notModifiable")}).
     */
    @Column(name = "modifiable", nullable = false)
    private Boolean modifiable = Boolean.TRUE;

    /**
     * Categorie de regroupement (libre). Ex. {@code "system"},
     * {@code "notification"}. Optionnel.
     */
    @Column(name = "categorie", length = 50)
    @Size(max = 50, message = "error.parametre.categorie.tooLong")
    private String categorie;

    public Parametre() {
        // JPA
    }

    public Long getParametreId() {
        return parametreId;
    }

    public void setParametreId(Long parametreId) {
        this.parametreId = parametreId;
    }

    public String getCle() {
        return cle;
    }

    public void setCle(String cle) {
        this.cle = cle;
    }

    public String getValeur() {
        return valeur;
    }

    public void setValeur(String valeur) {
        this.valeur = valeur;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Boolean getModifiable() {
        return modifiable;
    }

    public void setModifiable(Boolean modifiable) {
        this.modifiable = modifiable;
    }

    public String getCategorie() {
        return categorie;
    }

    public void setCategorie(String categorie) {
        this.categorie = categorie;
    }
}

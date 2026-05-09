package com.cityprojects.citybackend.common.audit;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Super-classe technique pour les entites JPA auditees.
 *
 * <p>Apporte les colonnes d'audit standard via {@link MappedSuperclass} :
 * {@code created_at}, {@code updated_at} (timestamps {@link Instant} UTC),
 * {@code created_by}, {@code updated_by} (username Spring Security, max 80 chars).</p>
 *
 * <h2>Contrat AuditorAware</h2>
 * <p>Les colonnes {@code createdBy}/{@code updatedBy} sont alimentees via
 * {@link com.cityprojects.citybackend.config.JpaAuditingConfig#auditorProvider()} :</p>
 * <ul>
 *   <li>utilisateur authentifie (SecurityContext) -&gt; username Spring Security ;</li>
 *   <li>boot / scheduler / batch sans auth -&gt; sentinel {@code "system"}.</li>
 * </ul>
 * <p>Une ligne avec {@code created_by = NULL} indique un INSERT contournant le
 * flow normal (ex. {@code SQL natif via repository.save} sans flush) et doit
 * etre investiguee.</p>
 *
 * <h2>Type {@link Instant}, pas {@code LocalDateTime}</h2>
 * <p>Le projet tourne en {@code Africa/Nouakchott} mais peut etre consomme
 * depuis d'autres fuseaux ; un timestamp UTC est neutre et non ambigu.</p>
 *
 * <h2>Pas d'equals/hashCode ici (et pas de {@code @Data} Lombok dans les sous-classes)</h2>
 * <p>Volontairement absents. Chaque sous-classe doit definir son propre contrat
 * en s'appuyant sur sa cle primaire (idealement via {@code Objects.equals(id, ...)}
 * apres flush, ou un equals base sur l'identite Hibernate).</p>
 * <p>Cf. {@code citybackend/CLAUDE.md} section 3.1 : <b>NE PAS</b> ajouter
 * {@code @Data} Lombok sur une entite JPA. Les equals/hashCode derives des
 * champs mutables sont dangereux sur entites JPA :</p>
 * <ul>
 *   <li>collections {@code @OneToMany}/{@code @ManyToMany} -&gt; recursion infinie ;</li>
 *   <li>proxies LAZY non initialises -&gt; LazyInitializationException pendant
 *       l'evaluation de hashCode ;</li>
 *   <li>identite qui change apres flush (id null -&gt; id genere) -&gt; entite
 *       perdue dans un Set/Map.</li>
 * </ul>
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class AuditableEntity {

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * NOT NULL aligne avec le SQL (cf. 002 + 020 : updated_at TIMESTAMP NOT NULL
     * default CURRENT_TIMESTAMP). {@link AuditingEntityListener} populate
     * updatedAt aussi a la creation (initial = createdAt), donc nullable=false
     * est tenable des le premier INSERT.
     */
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * NOT NULL aligne avec changeset 003.1 (sentinel "system" du
     * JpaAuditingConfig.auditorProvider() garantit non-null cote applicatif :
     * une ligne {@code created_by = NULL} indiquerait un INSERT contournant
     * le flow normal et doit etre investiguee).
     */
    @CreatedBy
    @Column(name = "created_by", length = 80, updatable = false, nullable = false)
    private String createdBy;

    /**
     * Reste nullable : aucune modification sur la creation initiale ne justifie
     * de forcer une valeur (Spring Data JPA appelle bien {@link AuditingEntityListener}
     * avec le username au pre-update, mais entre creation et premier update le
     * champ peut rester null si aucun change ne s'est produit). Pas de changeset
     * SQL associe (cf. 003-fix-client-schema-alignment.xml).
     */
    @LastModifiedBy
    @Column(name = "updated_by", length = 80)
    private String updatedBy;

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }
}

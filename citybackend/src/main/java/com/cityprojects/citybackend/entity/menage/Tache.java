package com.cityprojects.citybackend.entity.menage;

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
import jakarta.persistence.Version;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Tache de nettoyage / maintenance affectee a une chambre.
 *
 * <h3>Cycle de vie</h3>
 * <p>{@link StatutTache#PLANIFIEE} (creation) -&gt; {@link StatutTache#EN_COURS}
 * (commencer, {@code heureDebutReelle} pose) -&gt; {@link StatutTache#TERMINEE}
 * (terminer, {@code heureFinReelle} pose). {@link StatutTache#ANNULEE} est
 * possible avant le debut.</p>
 *
 * <h3>Cross-module (FK Long, pattern projet)</h3>
 * <ul>
 *   <li>{@code chambreId} : FK vers {@code hebergement.chambres} (obligatoire).</li>
 *   <li>{@code personnelId} : FK vers {@code menage.personnel} (nullable
 *       jusqu'a assignation).</li>
 * </ul>
 *
 * <h3>Heures previsionnelles vs reelles</h3>
 * <p>{@code heureDebutPrevue}/{@code heureFinPrevue} sont des {@link LocalTime}
 * (heure de la journee). {@code heureDebutReelle}/{@code heureFinReelle} sont
 * des {@link Instant} UTC (timestamps reels d'execution).</p>
 *
 * <h3>Note (changement vs mono source)</h3>
 * <p>Le mono utilisait une entite {@code StatutTache} de reference (table
 * separee). Pour reduire la dette technique, le statut est ici un enum
 * applicatif {@link StatutTache} stocke en VARCHAR. Le champ {@code dureeMinutes}
 * du mono (calcule par la DB) n'est pas porte ici : la duree se calcule
 * cote service ou client a partir des deux instants.</p>
 */
@Entity
@Table(name = "taches", schema = "menage")
public class Tache extends AuditableEntity implements TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tache_id")
    private Long tacheId;

    @TenantId
    @Column(name = "hotel_id", nullable = false, updatable = false)
    private Long hotelId;

    @NotNull
    @Column(name = "chambre_id", nullable = false)
    private Long chambreId;

    /** Nullable jusqu'a assignation. */
    @Column(name = "personnel_id")
    private Long personnelId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 20)
    private StatutTache statut = StatutTache.PLANIFIEE;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "type_nettoyage", nullable = false, length = 20)
    private TypeNettoyage typeNettoyage = TypeNettoyage.QUOTIDIEN;

    @NotNull
    @Min(1)
    @Max(3)
    @Column(name = "priorite", nullable = false)
    private Integer priorite = 1;

    @NotNull
    @Column(name = "date_planifiee", nullable = false)
    private LocalDate datePlanifiee;

    @Column(name = "heure_debut_prevue")
    private LocalTime heureDebutPrevue;

    @Column(name = "heure_fin_prevue")
    private LocalTime heureFinPrevue;

    @Column(name = "heure_debut_reelle")
    private Instant heureDebutReelle;

    @Column(name = "heure_fin_reelle")
    private Instant heureFinReelle;

    @Column(name = "commentaires", columnDefinition = "TEXT")
    private String commentaires;

    @Column(name = "problemes_detectes", columnDefinition = "TEXT")
    private String problemesDetectes;

    /**
     * Liste de materiel utilise sous forme libre (CSV ou JSON cote front).
     * VARCHAR(500) volontairement court : le menage n'utilise pas de gros
     * payloads.
     */
    @Column(name = "materiel_utilise", length = 500)
    private String materielUtilise;

    /**
     * Optimistic lock (Tour 30 etape 3) pour proteger les transitions
     * concurrentes (assigner / commencer / terminer / annuler). En cas de
     * conflit, Hibernate leve {@link jakarta.persistence.OptimisticLockException}
     * que le service traduit en {@code BusinessException("error.tache.concurrent.modification")}
     * (HTTP 409 Conflict).
     *
     * <p>Initialise a {@code 0L} cote applicatif ; la colonne SQL est
     * {@code BIGINT NOT NULL DEFAULT 0} (changeset 028, equipe DB).</p>
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    /** Constructeur JPA. */
    public Tache() {
    }

    public Long getTacheId() {
        return tacheId;
    }

    public void setTacheId(Long tacheId) {
        this.tacheId = tacheId;
    }

    @Override
    public Long getHotelId() {
        return hotelId;
    }

    @Override
    public void setHotelId(Long hotelId) {
        this.hotelId = hotelId;
    }

    public Long getChambreId() {
        return chambreId;
    }

    public void setChambreId(Long chambreId) {
        this.chambreId = chambreId;
    }

    public Long getPersonnelId() {
        return personnelId;
    }

    public void setPersonnelId(Long personnelId) {
        this.personnelId = personnelId;
    }

    public StatutTache getStatut() {
        return statut;
    }

    public void setStatut(StatutTache statut) {
        this.statut = statut;
    }

    public TypeNettoyage getTypeNettoyage() {
        return typeNettoyage;
    }

    public void setTypeNettoyage(TypeNettoyage typeNettoyage) {
        this.typeNettoyage = typeNettoyage;
    }

    public Integer getPriorite() {
        return priorite;
    }

    public void setPriorite(Integer priorite) {
        this.priorite = priorite;
    }

    public LocalDate getDatePlanifiee() {
        return datePlanifiee;
    }

    public void setDatePlanifiee(LocalDate datePlanifiee) {
        this.datePlanifiee = datePlanifiee;
    }

    public LocalTime getHeureDebutPrevue() {
        return heureDebutPrevue;
    }

    public void setHeureDebutPrevue(LocalTime heureDebutPrevue) {
        this.heureDebutPrevue = heureDebutPrevue;
    }

    public LocalTime getHeureFinPrevue() {
        return heureFinPrevue;
    }

    public void setHeureFinPrevue(LocalTime heureFinPrevue) {
        this.heureFinPrevue = heureFinPrevue;
    }

    public Instant getHeureDebutReelle() {
        return heureDebutReelle;
    }

    public void setHeureDebutReelle(Instant heureDebutReelle) {
        this.heureDebutReelle = heureDebutReelle;
    }

    public Instant getHeureFinReelle() {
        return heureFinReelle;
    }

    public void setHeureFinReelle(Instant heureFinReelle) {
        this.heureFinReelle = heureFinReelle;
    }

    public String getCommentaires() {
        return commentaires;
    }

    public void setCommentaires(String commentaires) {
        this.commentaires = commentaires;
    }

    public String getProblemesDetectes() {
        return problemesDetectes;
    }

    public void setProblemesDetectes(String problemesDetectes) {
        this.problemesDetectes = problemesDetectes;
    }

    public String getMaterielUtilise() {
        return materielUtilise;
    }

    public void setMaterielUtilise(String materielUtilise) {
        this.materielUtilise = materielUtilise;
    }

    public Long getVersion() {
        return version;
    }

    /**
     * Setter principalement utile pour les tests qui simulent un conflit
     * d'optimistic lock (modifier la version directement avant un save).
     * En production, Hibernate gere ce champ automatiquement.
     */
    public void setVersion(Long version) {
        this.version = version;
    }
}

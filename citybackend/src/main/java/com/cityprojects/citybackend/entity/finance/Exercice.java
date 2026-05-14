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

import java.time.LocalDate;

/**
 * Exercice comptable d'un hôtel (généralement année calendaire).
 *
 * <h3>Cycle de vie</h3>
 * <pre>
 *   OUVERT --&gt; EN_CLOTURE --&gt; CLOTURE
 * </pre>
 *
 * <p>Pendant {@code OUVERT} : factures et paiements acceptés à toute date
 * comprise entre {@code dateDebut} et {@code dateFin} inclus.</p>
 *
 * <p>Dès {@code EN_CLOTURE} ou {@code CLOTURE} : aucune écriture métier
 * (facture, paiement) ne peut être créée ni modifiée sur une date de cet
 * exercice. La garde est portée par
 * {@code ExerciceService.assertOuvert(LocalDate)}.</p>
 *
 * <h3>Auto-création</h3>
 * <p>Un exercice "année calendaire" est créé automatiquement lors de la
 * première écriture de l'année courante via
 * {@code ExerciceService.getOrCreateCurrent()}. Pour des exercices non
 * calendaires, créer manuellement un exercice avec les bonnes bornes avant
 * la première écriture.</p>
 *
 * <h3>Unicités</h3>
 * <ul>
 *   <li>{@code (hotel_id, code)} : un exercice avec le même code par hôtel
 *       (ex. {@code "2026"}).</li>
 *   <li>{@code (hotel_id, date_debut, date_fin)} : pas deux exercices avec
 *       exactement les mêmes bornes. Note : pas de protection SQL contre
 *       les chevauchements partiels — c'est la responsabilité du service de
 *       garantir qu'un seul exercice couvre une date donnée.</li>
 * </ul>
 */
@Entity
@Table(
        name = "exercice",
        schema = "finance",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_exercice_hotel_code",
                        columnNames = {"hotel_id", "code"}),
                @UniqueConstraint(
                        name = "uk_exercice_hotel_dates",
                        columnNames = {"hotel_id", "date_debut", "date_fin"})
        })
public class Exercice extends AuditableEntity implements TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @TenantId
    @Column(name = "hotel_id", nullable = false, updatable = false)
    private Long hotelId;

    @NotNull
    @Size(min = 1, max = 20)
    @Column(name = "code", nullable = false, length = 20)
    private String code;

    @NotNull
    @Column(name = "date_debut", nullable = false)
    private LocalDate dateDebut;

    @NotNull
    @Column(name = "date_fin", nullable = false)
    private LocalDate dateFin;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 20)
    private StatutExercice statut = StatutExercice.OUVERT;

    /** Date de clôture effective (null tant que statut == OUVERT). */
    @Column(name = "date_cloture")
    private LocalDate dateCloture;

    /** Utilisateur (username) ayant déclenché la clôture (null tant que OUVERT). */
    @Size(max = 100)
    @Column(name = "cloture_by", length = 100)
    private String clotureBy;

    /** Constructeur JPA. */
    public Exercice() {
    }

    /**
     * Indique si la date fournie est comprise dans cet exercice (bornes
     * inclusives). Helper pour la validation côté service.
     */
    public boolean contient(LocalDate date) {
        if (date == null || dateDebut == null || dateFin == null) {
            return false;
        }
        return !date.isBefore(dateDebut) && !date.isAfter(dateFin);
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

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
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

    public StatutExercice getStatut() {
        return statut;
    }

    public void setStatut(StatutExercice statut) {
        this.statut = statut;
    }

    public LocalDate getDateCloture() {
        return dateCloture;
    }

    public void setDateCloture(LocalDate dateCloture) {
        this.dateCloture = dateCloture;
    }

    public String getClotureBy() {
        return clotureBy;
    }

    public void setClotureBy(String clotureBy) {
        this.clotureBy = clotureBy;
    }
}

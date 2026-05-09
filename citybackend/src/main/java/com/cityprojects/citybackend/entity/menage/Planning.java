package com.cityprojects.citybackend.entity.menage;

import com.cityprojects.citybackend.common.audit.AuditableEntity;
import com.cityprojects.citybackend.common.tenant.TenantAware;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.TenantId;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Creneau de travail planifie pour un agent du personnel de menage.
 *
 * <p>Le service refuse les chevauchements pour un meme {@code personnelId} sur
 * une meme {@code dateTravail} (validation applicative). Le CHECK SQL
 * {@code chk_planning_heures} garantit que {@code heureFin > heureDebut}.</p>
 *
 * <p>{@code disponible=false} : creneau bloque (conge, maladie, RTT). Permet
 * de tracer une absence planifiee sans creer une entite "Conge" dediee.</p>
 *
 * <h3>Cross-module</h3>
 * <ul>
 *   <li>{@code personnelId} : FK vers {@code menage.personnel}.</li>
 * </ul>
 */
@Entity
@Table(name = "planning", schema = "menage")
public class Planning extends AuditableEntity implements TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "planning_id")
    private Long planningId;

    @TenantId
    @Column(name = "hotel_id", nullable = false, updatable = false)
    private Long hotelId;

    @NotNull
    @Column(name = "personnel_id", nullable = false)
    private Long personnelId;

    @NotNull
    @Column(name = "date_travail", nullable = false)
    private LocalDate dateTravail;

    @NotNull
    @Column(name = "heure_debut", nullable = false)
    private LocalTime heureDebut;

    @NotNull
    @Column(name = "heure_fin", nullable = false)
    private LocalTime heureFin;

    @Column(name = "disponible", nullable = false)
    private Boolean disponible = Boolean.TRUE;

    @Column(name = "commentaires", columnDefinition = "TEXT")
    private String commentaires;

    /** Constructeur JPA. */
    public Planning() {
    }

    public Long getPlanningId() {
        return planningId;
    }

    public void setPlanningId(Long planningId) {
        this.planningId = planningId;
    }

    @Override
    public Long getHotelId() {
        return hotelId;
    }

    @Override
    public void setHotelId(Long hotelId) {
        this.hotelId = hotelId;
    }

    public Long getPersonnelId() {
        return personnelId;
    }

    public void setPersonnelId(Long personnelId) {
        this.personnelId = personnelId;
    }

    public LocalDate getDateTravail() {
        return dateTravail;
    }

    public void setDateTravail(LocalDate dateTravail) {
        this.dateTravail = dateTravail;
    }

    public LocalTime getHeureDebut() {
        return heureDebut;
    }

    public void setHeureDebut(LocalTime heureDebut) {
        this.heureDebut = heureDebut;
    }

    public LocalTime getHeureFin() {
        return heureFin;
    }

    public void setHeureFin(LocalTime heureFin) {
        this.heureFin = heureFin;
    }

    public Boolean getDisponible() {
        return disponible;
    }

    public void setDisponible(Boolean disponible) {
        this.disponible = disponible;
    }

    public String getCommentaires() {
        return commentaires;
    }

    public void setCommentaires(String commentaires) {
        this.commentaires = commentaires;
    }
}

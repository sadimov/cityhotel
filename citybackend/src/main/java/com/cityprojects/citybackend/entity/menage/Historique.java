package com.cityprojects.citybackend.entity.menage;

import com.cityprojects.citybackend.common.audit.AuditableEntity;
import com.cityprojects.citybackend.common.tenant.TenantAware;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.TenantId;

import java.time.Instant;

/**
 * Audit log des actions sur les taches de menage.
 *
 * <p>Capture chaque transition metier (creation, assignation, debut, fin,
 * modification, suppression, annulation). {@code action} est libre (string)
 * pour ne pas figer l'enum cote front. {@code ancienStatut}/{@code nouveauStatut}
 * sont aussi des strings pour preserver l'historique meme apres une evolution
 * de l'enum {@link StatutTache}.</p>
 *
 * <h3>Cross-module</h3>
 * <ul>
 *   <li>{@code tacheId} : FK nullable vers {@code menage.taches}
 *       (ON DELETE SET NULL pour preserver l'historique apres suppression).</li>
 *   <li>{@code chambreId} : FK obligatoire vers {@code hebergement.chambres}.</li>
 *   <li>{@code personnelId} : FK nullable vers {@code menage.personnel}.</li>
 *   <li>{@code userId} : FK nullable vers {@code core.dbusers} (auteur de
 *       l'action ; null pour les actions automatiques scheduler/system).</li>
 * </ul>
 */
@Entity
@Table(name = "historique", schema = "menage")
public class Historique extends AuditableEntity implements TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "historique_id")
    private Long historiqueId;

    @TenantId
    @Column(name = "hotel_id", nullable = false, updatable = false)
    private Long hotelId;

    /** Nullable apres suppression d'une tache (FK ON DELETE SET NULL). */
    @Column(name = "tache_id")
    private Long tacheId;

    @NotNull
    @Column(name = "chambre_id", nullable = false)
    private Long chambreId;

    @Column(name = "personnel_id")
    private Long personnelId;

    @NotBlank
    @Size(max = 50)
    @Column(name = "action", nullable = false, length = 50)
    private String action;

    @Size(max = 50)
    @Column(name = "ancien_statut", length = 50)
    private String ancienStatut;

    @Size(max = 50)
    @Column(name = "nouveau_statut", length = 50)
    private String nouveauStatut;

    @Column(name = "commentaire", columnDefinition = "TEXT")
    private String commentaire;

    @Column(name = "user_id")
    private Long userId;

    @NotNull
    @Column(name = "timestamp_action", nullable = false)
    private Instant timestampAction;

    /** Constructeur JPA. */
    public Historique() {
    }

    public Long getHistoriqueId() {
        return historiqueId;
    }

    public void setHistoriqueId(Long historiqueId) {
        this.historiqueId = historiqueId;
    }

    @Override
    public Long getHotelId() {
        return hotelId;
    }

    @Override
    public void setHotelId(Long hotelId) {
        this.hotelId = hotelId;
    }

    public Long getTacheId() {
        return tacheId;
    }

    public void setTacheId(Long tacheId) {
        this.tacheId = tacheId;
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

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getAncienStatut() {
        return ancienStatut;
    }

    public void setAncienStatut(String ancienStatut) {
        this.ancienStatut = ancienStatut;
    }

    public String getNouveauStatut() {
        return nouveauStatut;
    }

    public void setNouveauStatut(String nouveauStatut) {
        this.nouveauStatut = nouveauStatut;
    }

    public String getCommentaire() {
        return commentaire;
    }

    public void setCommentaire(String commentaire) {
        this.commentaire = commentaire;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Instant getTimestampAction() {
        return timestampAction;
    }

    public void setTimestampAction(Instant timestampAction) {
        this.timestampAction = timestampAction;
    }
}

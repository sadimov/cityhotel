package com.cityprojects.citybackend.entity.finance;

import com.cityprojects.citybackend.common.tenant.TenantAware;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.TenantId;

import java.time.Instant;

/**
 * Audit trail des flux financiers et comptables (Bloc B3).
 *
 * <p>Une ligne par action significative (creation/emission/annulation
 * d'une facture, paiement, ecriture, exercice...). Materialise par
 * {@link com.cityprojects.citybackend.common.audit.AuditFinanceActionAspect}
 * apres execution reussie de la methode annotee
 * {@link com.cityprojects.citybackend.common.audit.AuditFinanceAction}.</p>
 *
 * <p>Tenant-aware ({@link TenantId}) - une ligne ne peut etre lue / mutee
 * que par son hotel d'origine. {@code @Auditable} non utilise (la table
 * EST l'audit, pas d'audit recursif).</p>
 */
@Entity
@Table(name = "audit_finance_log", schema = "finance")
public class AuditFinanceLog implements TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @TenantId
    @Column(name = "hotel_id", nullable = false, updatable = false)
    private Long hotelId;

    @NotBlank
    @Size(max = 60)
    @Column(name = "action", nullable = false, length = 60)
    private String action;

    @NotBlank
    @Size(max = 40)
    @Column(name = "entity_type", nullable = false, length = 40)
    private String entityType;

    @Column(name = "entity_id")
    private Long entityId;

    @Column(name = "user_id")
    private Long userId;

    @NotNull
    @Column(name = "event_at", nullable = false, updatable = false)
    private Instant eventAt;

    @Size(max = 2000)
    @Column(name = "payload", length = 2000)
    private String payload;

    public AuditFinanceLog() {
    }

    @PrePersist
    void prePersist() {
        if (eventAt == null) {
            eventAt = Instant.now();
        }
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

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public Long getEntityId() {
        return entityId;
    }

    public void setEntityId(Long entityId) {
        this.entityId = entityId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Instant getEventAt() {
        return eventAt;
    }

    public void setEventAt(Instant eventAt) {
        this.eventAt = eventAt;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }
}

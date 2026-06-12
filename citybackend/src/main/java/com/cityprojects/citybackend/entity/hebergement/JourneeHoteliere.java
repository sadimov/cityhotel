package com.cityprojects.citybackend.entity.hebergement;

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
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.TenantId;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Journée hôtelière matérialisée — concept comptable distinct du jour calendrier.
 *
 * <p>Cf. {@code règles_night_audit.txt} §1 : tant que le night audit n'a pas
 * tourné pour la date {@link #dateHotel}, le système reste « dans » cette
 * journée même si l'horloge serveur a changé de jour.</p>
 *
 * <h3>Invariants</h3>
 * <ul>
 *   <li>Une seule ligne {@code (OUVERTE | CLOTURE_EN_COURS)} par {@code hotel_id}
 *       à un instant donné (UNIQUE PARTIAL Postgres {@code uk_journee_hoteliere_hotel_actif}
 *       + garde applicative {@code HotelDayService} pour H2).</li>
 *   <li>Pas de doublon {@code (hotel_id, date_hotel)} (UNIQUE {@code uk_journee_hoteliere_hotel_date}).</li>
 *   <li>{@code hotel_id > 0} (sentinel ROOT 0 interdit, cf. {@code CityTenantIdentifierResolver}).</li>
 *   <li>{@code etat} ∈ {OUVERTE, CLOTURE_EN_COURS, CLOTUREE} (CHECK SQL + enum).</li>
 * </ul>
 *
 * <h3>Multi-tenant</h3>
 * <p>{@link TenantId} sur {@link #hotelId} : Hibernate ajoute automatiquement
 * {@code WHERE hotel_id = ?} sur SELECT et positionne la valeur à l'INSERT
 * depuis {@code CityTenantIdentifierResolver}. Aucun service ne doit setter
 * {@code hotelId} manuellement.</p>
 *
 * <h3>Concurrence</h3>
 * <p>{@link Version} (optimistic locking) : deux administrateurs qui tentent
 * de lancer le night audit en même temps verront le second échec être
 * remonté en 409 par {@code GlobalExceptionHandler} (cle {@code error.concurrent.modification}).</p>
 */
@Entity
@Table(
        name = "journee_hoteliere",
        schema = "hebergement",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_journee_hoteliere_hotel_date",
                columnNames = {"hotel_id", "date_hotel"}))
public class JourneeHoteliere extends AuditableEntity implements TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "hotel_id", nullable = false, updatable = false)
    private Long hotelId;

    @NotNull
    @Column(name = "date_hotel", nullable = false)
    private LocalDate dateHotel;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "etat", nullable = false, length = 20)
    private EtatJourneeHoteliere etat;

    @NotNull
    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "closed_by_user_id")
    private Long closedByUserId;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public JourneeHoteliere() {
    }

    // ── Getters / setters ────────────────────────────────────────────────────

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

    public LocalDate getDateHotel() {
        return dateHotel;
    }

    public void setDateHotel(LocalDate dateHotel) {
        this.dateHotel = dateHotel;
    }

    public EtatJourneeHoteliere getEtat() {
        return etat;
    }

    public void setEtat(EtatJourneeHoteliere etat) {
        this.etat = etat;
    }

    public Instant getOpenedAt() {
        return openedAt;
    }

    public void setOpenedAt(Instant openedAt) {
        this.openedAt = openedAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(Instant closedAt) {
        this.closedAt = closedAt;
    }

    public Long getClosedByUserId() {
        return closedByUserId;
    }

    public void setClosedByUserId(Long closedByUserId) {
        this.closedByUserId = closedByUserId;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}

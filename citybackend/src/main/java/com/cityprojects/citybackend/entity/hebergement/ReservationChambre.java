package com.cityprojects.citybackend.entity.hebergement;

import com.cityprojects.citybackend.common.audit.AuditableEntity;
import com.cityprojects.citybackend.common.tenant.TenantAware;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Pivot N:N entre une {@link Reservation} et une {@link Chambre}.
 *
 * <p>Une reservation peut comporter plusieurs chambres, eventuellement sur des
 * periodes differentes (sub-segment du sejour total). Le {@code prixNuit} fige
 * le tarif applique au moment de la reservation (ne depend plus du tarif
 * courant).</p>
 *
 * <p>Unicite par triplet (reservation, chambre, dateDebut) via
 * {@code uk_res_chambres}.</p>
 */
@Entity
@Table(
        name = "reservations_chambres",
        schema = "hebergement",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_res_chambres",
                columnNames = {"reservation_id", "chambre_id", "date_debut"}))
public class ReservationChambre extends AuditableEntity implements TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reservation_chambre_id")
    private Long reservationChambreId;

    @TenantId
    @Column(name = "hotel_id", nullable = false, updatable = false)
    private Long hotelId;

    @NotNull
    @Column(name = "reservation_id", nullable = false)
    private Long reservationId;

    @NotNull
    @Column(name = "chambre_id", nullable = false)
    private Long chambreId;

    @NotNull
    @Column(name = "date_debut", nullable = false)
    private LocalDate dateDebut;

    @NotNull
    @Column(name = "date_fin", nullable = false)
    private LocalDate dateFin;

    @NotNull
    @DecimalMin(value = "0.0", message = "error.reservationChambre.prixNuit.negative")
    @Column(name = "prix_nuit", nullable = false, precision = 10, scale = 2)
    private BigDecimal prixNuit;

    /** Constructeur JPA. */
    public ReservationChambre() {
    }

    public Long getReservationChambreId() {
        return reservationChambreId;
    }

    public void setReservationChambreId(Long reservationChambreId) {
        this.reservationChambreId = reservationChambreId;
    }

    @Override
    public Long getHotelId() {
        return hotelId;
    }

    @Override
    public void setHotelId(Long hotelId) {
        this.hotelId = hotelId;
    }

    public Long getReservationId() {
        return reservationId;
    }

    public void setReservationId(Long reservationId) {
        this.reservationId = reservationId;
    }

    public Long getChambreId() {
        return chambreId;
    }

    public void setChambreId(Long chambreId) {
        this.chambreId = chambreId;
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

    public BigDecimal getPrixNuit() {
        return prixNuit;
    }

    public void setPrixNuit(BigDecimal prixNuit) {
        this.prixNuit = prixNuit;
    }
}

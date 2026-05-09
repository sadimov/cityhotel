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
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.TenantId;

import java.math.BigDecimal;

/**
 * Client additionnel d'une reservation (compagnon de voyage).
 *
 * <p>{@code estPayant} et {@code pourcentageCharge} permettent une repartition
 * fine des couts (ex. groupe / famille / tour operator).</p>
 *
 * <p>Unicite par couple (reservation, client) via {@code uk_res_clients}.</p>
 */
@Entity
@Table(
        name = "reservations_clients",
        schema = "hebergement",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_res_clients",
                columnNames = {"reservation_id", "client_id"}))
public class ReservationClient extends AuditableEntity implements TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reservation_client_id")
    private Long reservationClientId;

    @TenantId
    @Column(name = "hotel_id", nullable = false, updatable = false)
    private Long hotelId;

    @NotNull
    @Column(name = "reservation_id", nullable = false)
    private Long reservationId;

    @NotNull
    @Column(name = "client_id", nullable = false)
    private Long clientId;

    /** Optionnel : peut etre rattache a une chambre specifique de la reservation. */
    @Column(name = "chambre_id")
    private Long chambreId;

    @NotNull
    @Column(name = "est_payant", nullable = false)
    private Boolean estPayant = Boolean.TRUE;

    @NotNull
    @DecimalMin(value = "0.00", message = "error.reservationClient.pourcentage.negative")
    @DecimalMax(value = "100.00", message = "error.reservationClient.pourcentage.tooHigh")
    @Column(name = "pourcentage_charge", nullable = false, precision = 5, scale = 2)
    private BigDecimal pourcentageCharge = BigDecimal.valueOf(100.00);

    /** Constructeur JPA. */
    public ReservationClient() {
    }

    public Long getReservationClientId() {
        return reservationClientId;
    }

    public void setReservationClientId(Long reservationClientId) {
        this.reservationClientId = reservationClientId;
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

    public Long getClientId() {
        return clientId;
    }

    public void setClientId(Long clientId) {
        this.clientId = clientId;
    }

    public Long getChambreId() {
        return chambreId;
    }

    public void setChambreId(Long chambreId) {
        this.chambreId = chambreId;
    }

    public Boolean getEstPayant() {
        return estPayant;
    }

    public void setEstPayant(Boolean estPayant) {
        this.estPayant = estPayant;
    }

    public BigDecimal getPourcentageCharge() {
        return pourcentageCharge;
    }

    public void setPourcentageCharge(BigDecimal pourcentageCharge) {
        this.pourcentageCharge = pourcentageCharge;
    }
}

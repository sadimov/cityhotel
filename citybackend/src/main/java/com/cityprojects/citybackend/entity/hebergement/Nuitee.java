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
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Detail jour-par-jour d'une reservation : 1 ligne = 1 nuit consommee par 1
 * chambre.
 *
 * <p>Genere a la confirmation de la reservation (statut PREVUE), passe en
 * CONSOMMEE post check-in, puis en FACTUREE quand la nuitee est rattachee a
 * une ligne de facture (FK {@code factureId} et {@code ligneFactureId} ajoutees
 * au Tour 19, changeset {@code 007-link-hebergement-finance.xml}).</p>
 *
 * <p>Le couple ({@code reservationId}, {@code chambreId}, {@code dateNuit}) est
 * unique via {@code uk_nuitees}.</p>
 */
@Entity
@Table(
        name = "nuitees",
        schema = "hebergement",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_nuitees",
                columnNames = {"reservation_id", "chambre_id", "date_nuit"}))
public class Nuitee extends AuditableEntity implements TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "nuitee_id")
    private Long nuiteeId;

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
    @Column(name = "date_nuit", nullable = false)
    private LocalDate dateNuit;

    @NotNull
    @DecimalMin(value = "0.0", message = "error.nuitee.prixNuit.negative")
    @Column(name = "prix_nuit", nullable = false, precision = 10, scale = 2)
    private BigDecimal prixNuit;

    @NotNull
    @DecimalMin(value = "0.0", message = "error.nuitee.taxeSejour.negative")
    @Column(name = "taxe_sejour", nullable = false, precision = 10, scale = 2)
    private BigDecimal taxeSejour = BigDecimal.ZERO;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 20)
    private StatutNuitee statut = StatutNuitee.PREVUE;

    /**
     * FK optionnelle vers {@code finance.factures} (Tour 19). {@code null} tant que
     * la nuitee n'est pas rattachee a une facture. Set par
     * {@code FactureService.fromReservation()} en meme temps que le statut
     * passe a {@code FACTUREE}.
     */
    @Column(name = "facture_id")
    private Long factureId;

    /**
     * FK optionnelle vers {@code finance.lignes_factures} (Tour 19). {@code null}
     * tant que la nuitee n'est pas rattachee a une ligne de facture.
     */
    @Column(name = "ligne_facture_id")
    private Long ligneFactureId;

    /** Constructeur JPA. */
    public Nuitee() {
    }

    public Long getNuiteeId() {
        return nuiteeId;
    }

    public void setNuiteeId(Long nuiteeId) {
        this.nuiteeId = nuiteeId;
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

    public LocalDate getDateNuit() {
        return dateNuit;
    }

    public void setDateNuit(LocalDate dateNuit) {
        this.dateNuit = dateNuit;
    }

    public BigDecimal getPrixNuit() {
        return prixNuit;
    }

    public void setPrixNuit(BigDecimal prixNuit) {
        this.prixNuit = prixNuit;
    }

    public BigDecimal getTaxeSejour() {
        return taxeSejour;
    }

    public void setTaxeSejour(BigDecimal taxeSejour) {
        this.taxeSejour = taxeSejour;
    }

    public StatutNuitee getStatut() {
        return statut;
    }

    public void setStatut(StatutNuitee statut) {
        this.statut = statut;
    }

    public Long getFactureId() {
        return factureId;
    }

    public void setFactureId(Long factureId) {
        this.factureId = factureId;
    }

    public Long getLigneFactureId() {
        return ligneFactureId;
    }

    public void setLigneFactureId(Long ligneFactureId) {
        this.ligneFactureId = ligneFactureId;
    }
}

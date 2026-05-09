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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Reservation d'un sejour pour un client principal sur une periode donnee.
 *
 * <h3>Numerotation</h3>
 * <p>{@code numero_reservation} est genere par hotel via
 * {@link com.cityprojects.citybackend.service.finance.NumerotationService}
 * (type {@link com.cityprojects.citybackend.service.finance.TypeNumerotation#RES}).
 * Format : {@code RES-{exercice}-{codePays}-{6 chiffres}}, ex.
 * {@code RES-2026-MR-000123}. Unicite garantie par hotel via
 * {@code UNIQUE (hotel_id, numero_reservation)} (contrainte
 * {@code uk_reservations_hotel_numero}).</p>
 *
 * <h3>References cross-module</h3>
 * <ul>
 *   <li>{@code clientPrincipalId} : FK vers {@code client.clients} (deja integre Tour 8).</li>
 *   <li>{@code societeId} : FK optionnelle vers {@code client.societes} (B2B).</li>
 *   <li>{@code userId} : FK vers {@code core.dbusers} (utilisateur ayant cree la reservation).</li>
 *   <li>{@code factureId} : FK optionnelle vers {@code finance.factures} (Tour 19,
 *       cf. changeset {@code 007-link-hebergement-finance.xml}). Reste {@code null}
 *       tant que la reservation n'a pas ete facturee. Renseigne par
 *       {@code FactureService.fromReservation()} quand toutes les nuitees
 *       CONSOMMEEs sont rattachees a une facture.</li>
 * </ul>
 *
 * <h3>Convention reference</h3>
 * <p>Toutes les FK sont stockees en {@code Long} (pas de {@code @ManyToOne}) :
 * cohesion avec le pattern projet (cf. {@code Client.societeId}). Evite les
 * proxies LAZY, les cycles de serialisation et les chargements involontaires
 * lors des mappers MapStruct.</p>
 *
 * <h3>{@code nb_nuits}</h3>
 * <p>Recalcule automatiquement par Hibernate via {@link PrePersist} et
 * {@link PreUpdate} : {@code dateDepart - dateArrivee} en jours
 * (cf. {@link #recalcNbNuits()}). N'est pas une colonne GENERATED Postgres
 * pour rester portable H2 en tests. Le service ne calcule plus la valeur
 * manuellement (Tour 12bis, finding codeC-1).</p>
 */
@Entity
@Table(
        name = "reservations",
        schema = "hebergement",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_reservations_hotel_numero",
                columnNames = {"hotel_id", "numero_reservation"}))
public class Reservation extends AuditableEntity implements TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reservation_id")
    private Long reservationId;

    @TenantId
    @Column(name = "hotel_id", nullable = false, updatable = false)
    private Long hotelId;

    /**
     * Genere par NumerotationService (RES-...). @NotNull et non @NotBlank :
     * la valeur est positionnee apres mapper.toEntity() et avant save() ;
     * @NotBlank refuserait l'instance pendant la fenetre transitoire.
     * Cf. meme commentaire sur Client.numeroClient.
     */
    @NotNull
    @Size(max = 40)
    @Column(name = "numero_reservation", nullable = false, length = 40)
    private String numeroReservation;

    @NotNull
    @Column(name = "client_principal_id", nullable = false)
    private Long clientPrincipalId;

    @Column(name = "societe_id")
    private Long societeId;

    @NotNull
    @Column(name = "date_arrivee", nullable = false)
    private LocalDate dateArrivee;

    @NotNull
    @Column(name = "date_depart", nullable = false)
    private LocalDate dateDepart;

    /**
     * Calcule automatiquement par {@link #recalcNbNuits()} ({@link PrePersist} /
     * {@link PreUpdate}) : {@code dateDepart - dateArrivee} en jours.
     * Pas GENERATED Postgres pour rester portable H2 en tests.
     */
    @Column(name = "nb_nuits")
    private Integer nbNuits;

    @PositiveOrZero
    @Column(name = "nb_adultes", nullable = false)
    private Integer nbAdultes = 1;

    @PositiveOrZero
    @Column(name = "nb_enfants", nullable = false)
    private Integer nbEnfants = 0;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 20)
    private StatutReservation statut = StatutReservation.CONFIRMEE;

    @Size(max = 100)
    @Column(name = "motif_sejour", length = 100)
    private String motifSejour;

    @Column(name = "commentaires", columnDefinition = "TEXT")
    private String commentaires;

    @Column(name = "reduction_pourcentage", precision = 5, scale = 2)
    private BigDecimal reductionPourcentage = BigDecimal.ZERO;

    @Column(name = "montant_total", precision = 12, scale = 2)
    private BigDecimal montantTotal = BigDecimal.ZERO;

    @NotNull
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * FK optionnelle vers {@code finance.factures} (Tour 19). {@code null} tant que
     * la reservation n'a pas ete facturee. Renseignee par
     * {@code FactureService.fromReservation()}. Stocke en {@code Long} (pas de
     * {@code @ManyToOne}) pour cohesion avec le pattern projet.
     */
    @Column(name = "facture_id")
    private Long factureId;

    /** Constructeur JPA. */
    public Reservation() {
    }

    /**
     * Recalcule {@code nbNuits} a chaque persist/update. Source unique de verite :
     * Hibernate (et non plus le service - cf. Tour 12bis, finding codeC-1).
     */
    @PrePersist
    @PreUpdate
    private void recalcNbNuits() {
        if (dateArrivee != null && dateDepart != null) {
            this.nbNuits = (int) ChronoUnit.DAYS.between(dateArrivee, dateDepart);
        }
    }

    public Long getReservationId() {
        return reservationId;
    }

    public void setReservationId(Long reservationId) {
        this.reservationId = reservationId;
    }

    @Override
    public Long getHotelId() {
        return hotelId;
    }

    @Override
    public void setHotelId(Long hotelId) {
        this.hotelId = hotelId;
    }

    public String getNumeroReservation() {
        return numeroReservation;
    }

    public void setNumeroReservation(String numeroReservation) {
        this.numeroReservation = numeroReservation;
    }

    public Long getClientPrincipalId() {
        return clientPrincipalId;
    }

    public void setClientPrincipalId(Long clientPrincipalId) {
        this.clientPrincipalId = clientPrincipalId;
    }

    public Long getSocieteId() {
        return societeId;
    }

    public void setSocieteId(Long societeId) {
        this.societeId = societeId;
    }

    public LocalDate getDateArrivee() {
        return dateArrivee;
    }

    public void setDateArrivee(LocalDate dateArrivee) {
        this.dateArrivee = dateArrivee;
    }

    public LocalDate getDateDepart() {
        return dateDepart;
    }

    public void setDateDepart(LocalDate dateDepart) {
        this.dateDepart = dateDepart;
    }

    public Integer getNbNuits() {
        return nbNuits;
    }

    public void setNbNuits(Integer nbNuits) {
        this.nbNuits = nbNuits;
    }

    public Integer getNbAdultes() {
        return nbAdultes;
    }

    public void setNbAdultes(Integer nbAdultes) {
        this.nbAdultes = nbAdultes;
    }

    public Integer getNbEnfants() {
        return nbEnfants;
    }

    public void setNbEnfants(Integer nbEnfants) {
        this.nbEnfants = nbEnfants;
    }

    public StatutReservation getStatut() {
        return statut;
    }

    public void setStatut(StatutReservation statut) {
        this.statut = statut;
    }

    public String getMotifSejour() {
        return motifSejour;
    }

    public void setMotifSejour(String motifSejour) {
        this.motifSejour = motifSejour;
    }

    public String getCommentaires() {
        return commentaires;
    }

    public void setCommentaires(String commentaires) {
        this.commentaires = commentaires;
    }

    public BigDecimal getReductionPourcentage() {
        return reductionPourcentage;
    }

    public void setReductionPourcentage(BigDecimal reductionPourcentage) {
        this.reductionPourcentage = reductionPourcentage;
    }

    public BigDecimal getMontantTotal() {
        return montantTotal;
    }

    public void setMontantTotal(BigDecimal montantTotal) {
        this.montantTotal = montantTotal;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getFactureId() {
        return factureId;
    }

    public void setFactureId(Long factureId) {
        this.factureId = factureId;
    }
}

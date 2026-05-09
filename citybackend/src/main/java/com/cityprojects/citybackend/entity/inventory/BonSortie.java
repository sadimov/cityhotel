package com.cityprojects.citybackend.entity.inventory;

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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.TenantId;

import java.time.LocalDate;

/**
 * Bon de sortie de stock (Restaurant, Bar, Menage, Maintenance, etc.).
 *
 * <h3>Numerotation</h3>
 * <p>{@code numero_bs} est genere par hotel via
 * {@link com.cityprojects.citybackend.service.finance.NumerotationService}
 * (type {@link com.cityprojects.citybackend.service.finance.TypeNumerotation#BS}).
 * Format : {@code BS-{exercice}-{codePays}-{6 chiffres}}, ex. {@code BS-2026-MR-000123}.
 * Unicite garantie par hotel via {@code UNIQUE (hotel_id, numero_bs)}.</p>
 *
 * <h3>Cycle de vie</h3>
 * <p>Voir {@link StatutBonSortie}. Le BS est cree {@code BROUILLON}, puis transitionne
 * vers {@code VALIDE} (verification stock disponible) puis {@code LIVRE}
 * (decremente le stock + genere MouvementStock SORTIE) ou {@code ANNULE}.</p>
 */
@Entity
@Table(
        name = "bons_sortie",
        schema = "inventory",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_bons_sortie_hotel_numero",
                columnNames = {"hotel_id", "numero_bs"}))
public class BonSortie extends AuditableEntity implements TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "bon_sortie_id")
    private Long bonSortieId;

    @TenantId
    @Column(name = "hotel_id", nullable = false, updatable = false)
    private Long hotelId;

    /**
     * Genere par NumerotationService (BS-...). @NotNull et non @NotBlank :
     * la valeur est positionnee apres mapper.toEntity() et avant save().
     */
    @NotNull
    @Size(max = 40)
    @Column(name = "numero_bs", nullable = false, length = 40)
    private String numeroBs;

    @NotBlank
    @Size(max = 100)
    @Column(name = "destination", nullable = false, length = 100)
    private String destination;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 20)
    private StatutBonSortie statut = StatutBonSortie.BROUILLON;

    @NotNull
    @Column(name = "date_sortie", nullable = false)
    private LocalDate dateSortie;

    @Column(name = "commentaires", columnDefinition = "TEXT")
    private String commentaires;

    @NotNull
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Constructeur JPA - initialise dateSortie a aujourd'hui. */
    public BonSortie() {
        this.dateSortie = LocalDate.now();
    }

    public Long getBonSortieId() {
        return bonSortieId;
    }

    public void setBonSortieId(Long bonSortieId) {
        this.bonSortieId = bonSortieId;
    }

    @Override
    public Long getHotelId() {
        return hotelId;
    }

    @Override
    public void setHotelId(Long hotelId) {
        this.hotelId = hotelId;
    }

    public String getNumeroBs() {
        return numeroBs;
    }

    public void setNumeroBs(String numeroBs) {
        this.numeroBs = numeroBs;
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public StatutBonSortie getStatut() {
        return statut;
    }

    public void setStatut(StatutBonSortie statut) {
        this.statut = statut;
    }

    public LocalDate getDateSortie() {
        return dateSortie;
    }

    public void setDateSortie(LocalDate dateSortie) {
        this.dateSortie = dateSortie;
    }

    public String getCommentaires() {
        return commentaires;
    }

    public void setCommentaires(String commentaires) {
        this.commentaires = commentaires;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }
}

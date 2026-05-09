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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.TenantId;

/**
 * Chambre physique de l'hotel (1 ligne = 1 chambre identifiee par son numero).
 *
 * <p>Reference le type de chambre via {@code typeId} (Long, pas {@code @ManyToOne} —
 * cf. convention projet : on stocke uniquement les FK techniques pour eviter
 * les proxies LAZY et les chargements en cascade involontaires lors des
 * mappers MapStruct).</p>
 *
 * <p>Le {@code numeroChambre} est unique par hotel via
 * {@code uk_chambres_hotel_numero}.</p>
 *
 * <p>Le statut est une transition controlee (cf. {@link StatutChambre}).</p>
 */
@Entity
@Table(
        name = "chambres",
        schema = "hebergement",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_chambres_hotel_numero",
                columnNames = {"hotel_id", "numero_chambre"}))
public class Chambre extends AuditableEntity implements TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "chambre_id")
    private Long chambreId;

    @TenantId
    @Column(name = "hotel_id", nullable = false, updatable = false)
    private Long hotelId;

    @NotBlank
    @Size(max = 10)
    @Column(name = "numero_chambre", nullable = false, length = 10)
    private String numeroChambre;

    @NotNull
    @Column(name = "type_id", nullable = false)
    private Long typeId;

    @Column(name = "etage")
    private Integer etage;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 20)
    private StatutChambre statut = StatutChambre.DISPONIBLE;

    @NotNull
    @Positive
    @Column(name = "nb_lits", nullable = false)
    private Integer nbLits;

    @NotNull
    @Positive
    @Column(name = "nb_personnes_max", nullable = false)
    private Integer nbPersonnesMax;

    /**
     * Equipements serialises en JSON (TEXT cote SQL pour rester portable H2 ;
     * Postgres supporterait jsonb natif - revisitable dans un changeset additif).
     */
    @Column(name = "equipements", columnDefinition = "TEXT")
    private String equipements;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "actif", nullable = false)
    private Boolean actif = Boolean.TRUE;

    /** Constructeur JPA. */
    public Chambre() {
    }

    public Long getChambreId() {
        return chambreId;
    }

    public void setChambreId(Long chambreId) {
        this.chambreId = chambreId;
    }

    @Override
    public Long getHotelId() {
        return hotelId;
    }

    @Override
    public void setHotelId(Long hotelId) {
        this.hotelId = hotelId;
    }

    public String getNumeroChambre() {
        return numeroChambre;
    }

    public void setNumeroChambre(String numeroChambre) {
        this.numeroChambre = numeroChambre;
    }

    public Long getTypeId() {
        return typeId;
    }

    public void setTypeId(Long typeId) {
        this.typeId = typeId;
    }

    public Integer getEtage() {
        return etage;
    }

    public void setEtage(Integer etage) {
        this.etage = etage;
    }

    public StatutChambre getStatut() {
        return statut;
    }

    public void setStatut(StatutChambre statut) {
        this.statut = statut;
    }

    public Integer getNbLits() {
        return nbLits;
    }

    public void setNbLits(Integer nbLits) {
        this.nbLits = nbLits;
    }

    public Integer getNbPersonnesMax() {
        return nbPersonnesMax;
    }

    public void setNbPersonnesMax(Integer nbPersonnesMax) {
        this.nbPersonnesMax = nbPersonnesMax;
    }

    public String getEquipements() {
        return equipements;
    }

    public void setEquipements(String equipements) {
        this.equipements = equipements;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Boolean getActif() {
        return actif;
    }

    public void setActif(Boolean actif) {
        this.actif = actif;
    }
}

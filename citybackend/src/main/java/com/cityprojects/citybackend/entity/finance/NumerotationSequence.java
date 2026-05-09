package com.cityprojects.citybackend.entity.finance;

import com.cityprojects.citybackend.common.audit.AuditableEntity;
import com.cityprojects.citybackend.common.tenant.TenantAware;
import com.cityprojects.citybackend.service.finance.TypeNumerotation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.TenantId;

/**
 * Compteur de numerotation comptable, segmente par hotel x type x exercice.
 * <p>
 * Une ligne par triplet : la ligne du segment actif est mise a jour via
 * verrou pessimiste (cf.
 * {@link com.cityprojects.citybackend.repository.finance.NumerotationSequenceRepository#findByTypeAndExerciceForUpdate}).
 *
 * <h3>Pourquoi cle technique + UNIQUE INDEX et non PK composite ?</h3>
 * <p>Hibernate 6 INTERDIT la cohabitation
 * {@code @Id} + {@link org.hibernate.annotations.TenantId} sur le meme champ
 * (erreur a l'init : {@code "Property ... is annotated 'TenantId' which is
 * not an '@IdGeneratorType'"}). On utilise donc :</p>
 * <ul>
 *   <li>une cle technique {@code id BIGSERIAL} pour satisfaire JPA et le
 *       contrat de @Id pur,</li>
 *   <li>un {@code @UniqueConstraint(hotel_id, type, exercice)} pour garantir
 *       l'unicite metier,</li>
 *   <li>{@code hotelId} reste {@code @TenantId} : Hibernate ajoute toujours
 *       {@code WHERE hotel_id = ?} a tous les SELECT/UPDATE/DELETE et
 *       populate la colonne a l'INSERT depuis le resolver.</li>
 * </ul>
 * Cote SQL le changeset Liquibase 020 le reflete : {@code id BIGSERIAL PK}
 * + {@code UNIQUE (hotel_id, type, exercice)}.
 *
 * <p>Heritage de {@link AuditableEntity} : created_at / updated_at /
 * created_by / updated_by automatiques via Spring Data JPA Auditing.</p>
 */
@Entity
@Table(
        name = "numerotation_sequence",
        schema = "finance",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_numerotation_hotel_type_exercice",
                columnNames = {"hotel_id", "type", "exercice"}))
public class NumerotationSequence extends AuditableEntity implements TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @TenantId
    @Column(name = "hotel_id", nullable = false, updatable = false)
    private Long hotelId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 10, nullable = false, updatable = false)
    private TypeNumerotation type;

    @Column(name = "exercice", nullable = false, updatable = false)
    private Integer exercice;

    @Column(name = "last_value", nullable = false)
    private Long lastValue = 0L;

    /** Constructeur JPA. */
    public NumerotationSequence() {
    }

    /**
     * Construit un nouveau compteur initialise a 0 pour le triplet donne.
     * Utilise par le service quand aucun compteur n'existe encore pour
     * l'exercice courant.
     * <p>
     * Note : {@code hotelId} est passe explicitement pour que la valeur soit
     * disponible cote applicatif et coherente avec les attentes (audit,
     * debug). A l'INSERT, Hibernate va de toute facon re-resoudre la valeur
     * via le {@code CityTenantIdentifierResolver}.
     */
    public NumerotationSequence(Long hotelId, TypeNumerotation type, Integer exercice) {
        this.hotelId = hotelId;
        this.type = type;
        this.exercice = exercice;
        this.lastValue = 0L;
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

    public TypeNumerotation getType() {
        return type;
    }

    public void setType(TypeNumerotation type) {
        this.type = type;
    }

    public Integer getExercice() {
        return exercice;
    }

    public void setExercice(Integer exercice) {
        this.exercice = exercice;
    }

    public Long getLastValue() {
        return lastValue;
    }

    public void setLastValue(Long lastValue) {
        this.lastValue = lastValue;
    }
}

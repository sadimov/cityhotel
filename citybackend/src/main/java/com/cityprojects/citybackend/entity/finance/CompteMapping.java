package com.cityprojects.citybackend.entity.finance;

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
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.TenantId;

/**
 * Mapping comptable par hôtel : associe un {@link TypeEvenementComptable}
 * (déclencheur métier) à un code de compte du Plan Comptable Général.
 *
 * <p>Tenant-aware : chaque hôtel peut surcharger les codes de comptes par
 * défaut (cf. {@link TypeEvenementComptable#defaultCompteCode()}) selon ses
 * besoins (ex. ventiler les nuitées sur plusieurs sous-comptes 706xxx pour
 * un suivi analytique).</p>
 *
 * <p>Unicité : {@code (hotel_id, type_evenement)} - un seul mapping par
 * couple hôtel/événement. Le mapping pointe vers un {@code compte_code} qui
 * doit exister dans {@code finance.plan_comptable_general} et être
 * {@code utilisable = true}.</p>
 *
 * <p>Création : automatique à la création d'un nouvel hôtel (cf.
 * {@code CompteMappingInitializer} qui seed les valeurs par défaut depuis
 * {@code TypeEvenementComptable.defaultCompteCode()}). Modification ultérieure
 * via {@code PUT /api/finance/compte-mapping/{typeEvenement}}.</p>
 */
@Entity
@Table(
        name = "compte_mapping",
        schema = "finance",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_compte_mapping_hotel_type",
                columnNames = {"hotel_id", "type_evenement"}))
public class CompteMapping extends AuditableEntity implements TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @TenantId
    @Column(name = "hotel_id", nullable = false, updatable = false)
    private Long hotelId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "type_evenement", nullable = false, length = 40)
    private TypeEvenementComptable typeEvenement;

    @NotNull
    @Size(min = 1, max = 10)
    @Column(name = "compte_code", nullable = false, length = 10)
    private String compteCode;

    @NotNull
    @Column(name = "actif", nullable = false)
    private Boolean actif = Boolean.TRUE;

    /** Constructeur JPA. */
    public CompteMapping() {
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

    public TypeEvenementComptable getTypeEvenement() {
        return typeEvenement;
    }

    public void setTypeEvenement(TypeEvenementComptable typeEvenement) {
        this.typeEvenement = typeEvenement;
    }

    public String getCompteCode() {
        return compteCode;
    }

    public void setCompteCode(String compteCode) {
        this.compteCode = compteCode;
    }

    public Boolean getActif() {
        return actif;
    }

    public void setActif(Boolean actif) {
        this.actif = actif;
    }
}

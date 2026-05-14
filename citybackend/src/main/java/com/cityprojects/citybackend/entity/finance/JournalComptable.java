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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.TenantId;

/**
 * Journal comptable par hotel.
 *
 * <p>Un journal regroupe les ecritures comptables d'une meme famille
 * fonctionnelle (ventes, achats, tresorerie, operations diverses, avoirs).
 * Chaque hotel dispose de ses propres journaux : la table est donc
 * tenant-scopee ({@code @TenantId}).</p>
 *
 * <h3>Journaux seedes par defaut</h3>
 * <p>A la creation d'un nouvel hotel
 * ({@link com.cityprojects.citybackend.service.admin.HotelAdminServiceImpl}),
 * le {@code JournalComptableInitializer} provisionne automatiquement les
 * six journaux standards :</p>
 * <ul>
 *   <li>{@code VTE} - Ventes ({@link TypeJournal#VENTE})</li>
 *   <li>{@code ACH} - Achats ({@link TypeJournal#ACHAT})</li>
 *   <li>{@code BAN} - Banque ({@link TypeJournal#TRESORERIE})</li>
 *   <li>{@code CAI} - Caisse ({@link TypeJournal#TRESORERIE})</li>
 *   <li>{@code OD}  - Operations Diverses ({@link TypeJournal#OPERATION_DIVERSE})</li>
 *   <li>{@code AVO} - Avoirs ({@link TypeJournal#AVOIR})</li>
 * </ul>
 *
 * <h3>Numerotation</h3>
 * <p>Le code du journal sert de discriminant a la
 * {@link com.cityprojects.citybackend.service.finance.NumerotationService}
 * pour generer les numeros d'ecriture : {@code JRN-VTE-2026-MR-000001},
 * {@code JRN-ACH-2026-MR-000001}, etc. La sequence est segmentee par
 * (hotel, JRN, exercice, codeJournal) - chaque journal a sa propre suite.</p>
 *
 * <h3>Desactivation</h3>
 * <p>Le flag {@code actif} permet de bloquer la creation de nouvelles
 * ecritures sur un journal sans le supprimer (les ecritures historiques
 * restent intactes pour l'audit). Reactivation possible par les
 * SUPERADMIN/ADMIN.</p>
 */
@Entity
@Table(
        name = "journal_comptable",
        schema = "finance",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_journal_comptable_hotel_code",
                columnNames = {"hotel_id", "code"}))
public class JournalComptable extends AuditableEntity implements TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @TenantId
    @Column(name = "hotel_id", nullable = false, updatable = false)
    private Long hotelId;

    @NotBlank
    @Size(min = 1, max = 5)
    @Column(name = "code", nullable = false, length = 5)
    private String code;

    @NotBlank
    @Size(min = 1, max = 100)
    @Column(name = "libelle", nullable = false, length = 100)
    private String libelle;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private TypeJournal type;

    @NotNull
    @Column(name = "actif", nullable = false)
    private Boolean actif = Boolean.TRUE;

    /** Constructeur JPA. */
    public JournalComptable() {
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

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getLibelle() {
        return libelle;
    }

    public void setLibelle(String libelle) {
        this.libelle = libelle;
    }

    public TypeJournal getType() {
        return type;
    }

    public void setType(TypeJournal type) {
        this.type = type;
    }

    public Boolean getActif() {
        return actif;
    }

    public void setActif(Boolean actif) {
        this.actif = actif;
    }

    /** Helper boolean intrinseque (pour les Stream::filter). */
    public boolean isActif() {
        return Boolean.TRUE.equals(actif);
    }
}

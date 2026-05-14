package com.cityprojects.citybackend.repository.finance;

import com.cityprojects.citybackend.dto.reporting.projection.LigneFactureMonthProjection;
import com.cityprojects.citybackend.dto.reporting.projection.TvaRecapProjection;
import com.cityprojects.citybackend.entity.finance.LigneFacture;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * Repository des lignes de facture.
 *
 * <p>{@code LigneFacture} porte {@code @TenantId hotelId} depuis B1
 * (2026-05-08). Toutes les requetes Spring Data sont donc filtrees
 * automatiquement par Hibernate via le resolver de tenant courant.</p>
 */
@Repository
public interface LigneFactureRepository extends JpaRepository<LigneFacture, Long> {

    List<LigneFacture> findByFactureIdOrderByLigneFactureIdAsc(Long factureId);

    /** Supprime toutes les lignes d'une facture (utilise avant recalcul). */
    void deleteByFactureId(Long factureId);

    /** Existe-t-il une ligne deja liee a cette nuitee ? Pour idempotence. */
    boolean existsByNuiteeId(Long nuiteeId);

    /**
     * Tour 45 — Toutes les lignes des factures rattachees a une reservation,
     * triees par {@code factureId} puis {@code ligneFactureId}.
     *
     * <p>Jointure {@code LigneFacture -> Facture} filtree par
     * {@code Facture.reservationId} ; le filtre tenant est pose automatiquement
     * sur {@code Facture} ET sur {@code LigneFacture} par Hibernate
     * ({@code @TenantId}), donc la requete est tenant-safe.</p>
     */
    @Query("SELECT l FROM LigneFacture l, Facture f "
            + "WHERE l.factureId = f.factureId "
            + "AND f.reservationId = :reservationId "
            + "ORDER BY l.factureId ASC, l.ligneFactureId ASC")
    List<LigneFacture> findByReservationId(@Param("reservationId") Long reservationId);

    // ============================================================================
    // Tour 41 — Reporting P1/P2 : agregats TVA pour R-FIN-003.
    // Filtre tenant ajoute automatiquement par Hibernate via @TenantId.
    // ============================================================================

    /**
     * Agregat TVA par TAUX (R-FIN-003) sur la plage [from, to) basee sur la
     * {@code dateFacture} de la facture parente. Exclut les factures ANNULEE.
     */
    @Query("SELECT CAST(l.tauxTva AS string) AS dimension, "
            + "  COALESCE(SUM(l.montantHt), 0) AS totalHt, "
            + "  COALESCE(SUM(l.montantTva), 0) AS totalTva, "
            + "  COALESCE(SUM(l.montantTtc), 0) AS totalTtc "
            + "FROM LigneFacture l, Facture f "
            + "WHERE l.factureId = f.factureId "
            + "AND f.dateFacture >= :from AND f.dateFacture < :to "
            + "AND f.statut <> com.cityprojects.citybackend.entity.finance.StatutFacture.ANNULEE "
            + "GROUP BY l.tauxTva "
            + "ORDER BY l.tauxTva ASC")
    List<TvaRecapProjection> aggregateTvaByTaux(@Param("from") LocalDate from,
                                                @Param("to") LocalDate to);

    /**
     * Agregat TVA total (R-FIN-003) sur la plage : retourne {@code [totalHt, totalTva, totalTtc]}.
     */
    @Query("SELECT COALESCE(SUM(l.montantHt), 0), "
            + "  COALESCE(SUM(l.montantTva), 0), "
            + "  COALESCE(SUM(l.montantTtc), 0) "
            + "FROM LigneFacture l, Facture f "
            + "WHERE l.factureId = f.factureId "
            + "AND f.dateFacture >= :from AND f.dateFacture < :to "
            + "AND f.statut <> com.cityprojects.citybackend.entity.finance.StatutFacture.ANNULEE")
    Object[] aggregateTvaTotal(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * Lignes de facture sur la plage avec {@code dateFacture} jointe — utilise
     * par le groupage MOIS cote service (R-FIN-003).
     */
    @Query("SELECT f.dateFacture AS dateFacture, "
            + "  l.montantHt AS montantHt, "
            + "  l.montantTva AS montantTva, "
            + "  l.montantTtc AS montantTtc "
            + "FROM LigneFacture l, Facture f "
            + "WHERE l.factureId = f.factureId "
            + "AND f.dateFacture >= :from AND f.dateFacture < :to "
            + "AND f.statut <> com.cityprojects.citybackend.entity.finance.StatutFacture.ANNULEE")
    List<LigneFactureMonthProjection> findLignesOnRangeWithDate(@Param("from") LocalDate from,
                                                                @Param("to") LocalDate to);
}

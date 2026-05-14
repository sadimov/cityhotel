package com.cityprojects.citybackend.repository.finance;

import com.cityprojects.citybackend.dto.reporting.projection.CARecapProjection;
import com.cityprojects.citybackend.dto.reporting.projection.TopClientProjection;
import com.cityprojects.citybackend.dto.reporting.projection.TopSocieteProjection;
import com.cityprojects.citybackend.entity.finance.Facture;
import com.cityprojects.citybackend.entity.finance.StatutFacture;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repository des factures.
 *
 * <p>Le filtre {@code WHERE hotel_id = ?} est ajoute automatiquement par
 * Hibernate via {@code @TenantId}. Toutes les methodes ci-dessous voient donc
 * uniquement les factures du tenant courant.</p>
 */
@Repository
public interface FactureRepository
        extends JpaRepository<Facture, Long>, JpaSpecificationExecutor<Facture> {

    Optional<Facture> findByNumeroFacture(String numeroFacture);

    boolean existsByNumeroFacture(String numeroFacture);

    /** Page des factures par statut, plus recentes d'abord. */
    Page<Facture> findByStatutOrderByDateFactureDesc(StatutFacture statut, Pageable pageable);

    /** Toutes les factures liees a une reservation donnee. */
    List<Facture> findByReservationId(Long reservationId);

    /** Toutes les factures liees a un client donne, plus recentes d'abord. */
    Page<Facture> findByClientIdOrderByDateFactureDesc(Long clientId, Pageable pageable);

    /** Toutes les factures liees a un compte donne. */
    Page<Facture> findByCompteIdOrderByDateFactureDesc(Long compteId, Pageable pageable);

    /** Factures echues (non payees, dont l'echeance est passee). */
    List<Facture> findByDateEcheanceBeforeAndStatutNot(LocalDate date, StatutFacture statut);

    // ============================================================================
    // Tour 40 — Reporting MVP : agregats pour rapports R-FIN-001 et R-CLI-001.
    // Le filtre tenant est ajoute automatiquement par Hibernate via @TenantId.
    // Toutes les requetes excluent les factures ANNULEE (non comptabilisees).
    // ============================================================================

    /**
     * Agregats CA sur la plage [from, to) (R-FIN-001). Exclut ANNULEE.
     * COALESCE pour eviter NULL si aucune facture.
     */
    @Query("SELECT "
            + " COUNT(f) AS nbFactures, "
            + " COALESCE(SUM(f.montantHt), 0) AS caEmisHt, "
            + " COALESCE(SUM(f.montantTva), 0) AS caEmisTva, "
            + " COALESCE(SUM(f.montantTtc), 0) AS caEmisTtc, "
            + " COALESCE(SUM(f.montantPaye), 0) AS caPayeTtc "
            + "FROM Facture f "
            + "WHERE f.dateFacture >= :from AND f.dateFacture < :to "
            + "AND f.statut <> com.cityprojects.citybackend.entity.finance.StatutFacture.ANNULEE")
    CARecapProjection aggregateCaOnRange(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * Top clients par CA TTC sur la plage [from, to) (R-CLI-001).
     *
     * <p>Jointure {@code Facture} - {@code Client} via {@code clientId} (les deux entites
     * sont @TenantId, donc Hibernate ajoute le filtre sur les deux). Exclut ANNULEE et
     * les factures sans clientId (factures interne / fournisseur).</p>
     */
    @Query("SELECT "
            + " c.clientId AS clientId, "
            + " c.numeroClient AS numeroClient, "
            + " c.nom AS nom, "
            + " c.prenom AS prenom, "
            + " COUNT(f) AS nbFactures, "
            + " COALESCE(SUM(f.montantTtc), 0) AS caTtc, "
            + " COALESCE(SUM(f.montantPaye), 0) AS caPaye "
            + "FROM Facture f, com.cityprojects.citybackend.entity.client.Client c "
            + "WHERE f.clientId = c.clientId "
            + "AND f.dateFacture >= :from AND f.dateFacture < :to "
            + "AND f.statut <> com.cityprojects.citybackend.entity.finance.StatutFacture.ANNULEE "
            + "GROUP BY c.clientId, c.numeroClient, c.nom, c.prenom "
            + "ORDER BY SUM(f.montantTtc) DESC, c.nom ASC")
    List<TopClientProjection> findTopClientsByPeriode(@Param("from") LocalDate from,
                                                      @Param("to") LocalDate to,
                                                      Pageable pageable);

    // ============================================================================
    // Tour 41 — Reporting P1/P2 : R-FIN-002 (encours), R-FIN-004 (top societes).
    // Filtre tenant ajoute automatiquement par Hibernate via @TenantId.
    // ============================================================================

    /**
     * Liste les factures non soldees (statut != PAYEE et != ANNULEE) ayant un
     * {@code montantTtc &gt; montantPaye}. Utilisee par R-FIN-002 (encours).
     */
    @Query("SELECT f FROM Facture f "
            + "WHERE f.statut NOT IN ("
            + "  com.cityprojects.citybackend.entity.finance.StatutFacture.PAYEE, "
            + "  com.cityprojects.citybackend.entity.finance.StatutFacture.ANNULEE) "
            + "AND f.montantTtc > f.montantPaye "
            + "ORDER BY f.dateFacture ASC")
    List<Facture> findFacturesNonSoldees();

    /**
     * Top societes par CA TTC sur la plage [from, to) (R-FIN-004).
     */
    @Query("SELECT "
            + " s.societeId AS societeId, "
            + " s.societeNom AS societeNom, "
            + " s.siret AS siret, "
            + " COUNT(f) AS nbFactures, "
            + " COALESCE(SUM(f.montantTtc), 0) AS caTtc, "
            + " COALESCE(SUM(f.montantPaye), 0) AS caPaye "
            + "FROM Facture f, com.cityprojects.citybackend.entity.client.Societe s "
            + "WHERE f.societeId = s.societeId "
            + "AND f.dateFacture >= :from AND f.dateFacture < :to "
            + "AND f.statut <> com.cityprojects.citybackend.entity.finance.StatutFacture.ANNULEE "
            + "GROUP BY s.societeId, s.societeNom, s.siret "
            + "ORDER BY SUM(f.montantTtc) DESC, s.societeNom ASC")
    List<TopSocieteProjection> findTopSocietesByPeriode(@Param("from") LocalDate from,
                                                        @Param("to") LocalDate to,
                                                        Pageable pageable);
}

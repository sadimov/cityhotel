package com.cityprojects.citybackend.repository.finance;

import com.cityprojects.citybackend.dto.reporting.projection.PaiementModeProjection;
import com.cityprojects.citybackend.entity.finance.Paiement;
import com.cityprojects.citybackend.entity.finance.StatutPaiement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repository des paiements (Hibernate filtre auto via @TenantId).
 */
@Repository
public interface PaiementRepository
        extends JpaRepository<Paiement, Long>, JpaSpecificationExecutor<Paiement> {

    Optional<Paiement> findByNumeroPaiement(String numeroPaiement);

    boolean existsByNumeroPaiement(String numeroPaiement);

    Page<Paiement> findByStatutOrderByDatePaiementDesc(StatutPaiement statut, Pageable pageable);

    Page<Paiement> findByCompteIdOrderByDatePaiementDesc(Long compteId, Pageable pageable);

    /**
     * Tous les paiements d'un jour donne, filtres par statut. Utilise par la
     * cloture de caisse journaliere (Tour 26.1) pour ne retenir que les
     * paiements {@code VALIDE} (les ANNULE/EN_ATTENTE/REFUSE sont exclus du
     * total caisse). Hibernate ajoute auto le filtre tenant.
     */
    List<Paiement> findByDatePaiementAndStatut(LocalDate datePaiement, StatutPaiement statut);

    // ============================================================================
    // Tour 40 — Reporting MVP : agregats pour R-FIN-001 (CA recap).
    // Filtre tenant ajoute automatiquement par Hibernate via @TenantId.
    // ============================================================================

    /**
     * Compte les paiements VALIDE sur la plage [from, to) (R-FIN-001).
     */
    @Query("SELECT COUNT(p) FROM Paiement p "
            + "WHERE p.datePaiement >= :from AND p.datePaiement < :to "
            + "AND p.statut = com.cityprojects.citybackend.entity.finance.StatutPaiement.VALIDE")
    long countValidesOnRange(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * Somme {@code montantTotal} des paiements VALIDE sur la plage [from, to) (R-FIN-001).
     */
    @Query("SELECT COALESCE(SUM(p.montantTotal), 0) FROM Paiement p "
            + "WHERE p.datePaiement >= :from AND p.datePaiement < :to "
            + "AND p.statut = com.cityprojects.citybackend.entity.finance.StatutPaiement.VALIDE")
    BigDecimal sumMontantValidesOnRange(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * Agregat paiements VALIDE groupes par {@code modePaiement} a une date donnee
     * (R-RES-001 journal de caisse).
     */
    @Query("SELECT p.modePaiement AS modePaiement, "
            + "  COUNT(p) AS nbPaiements, "
            + "  COALESCE(SUM(p.montantTotal), 0) AS montantTotal "
            + "FROM Paiement p "
            + "WHERE p.datePaiement = :date "
            + "AND p.statut = com.cityprojects.citybackend.entity.finance.StatutPaiement.VALIDE "
            + "GROUP BY p.modePaiement "
            + "ORDER BY SUM(p.montantTotal) DESC")
    List<PaiementModeProjection> aggregateByModeOnDate(@Param("date") LocalDate date);
}

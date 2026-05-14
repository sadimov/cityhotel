package com.cityprojects.citybackend.repository.hebergement;

import com.cityprojects.citybackend.dto.reporting.projection.NuiteeOccupationProjection;
import com.cityprojects.citybackend.entity.hebergement.Nuitee;
import com.cityprojects.citybackend.entity.hebergement.StatutNuitee;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * Repository des nuitees (1 ligne = 1 nuit / 1 chambre / 1 reservation).
 *
 * <p>Hibernate ajoute automatiquement {@code WHERE hotel_id = ?} via
 * {@link org.hibernate.annotations.TenantId}.</p>
 */
@Repository
public interface NuiteeRepository
        extends JpaRepository<Nuitee, Long>, JpaSpecificationExecutor<Nuitee> {

    /** Liste les nuitees d'une reservation, par date croissante. */
    List<Nuitee> findByReservationIdOrderByDateNuitAsc(Long reservationId);

    /** Liste les nuitees d'une reservation a un statut donne. */
    List<Nuitee> findByReservationIdAndStatutOrderByDateNuitAsc(Long reservationId, StatutNuitee statut);

    /**
     * Garde d'idempotence pour la generation des nuitees lors de la creation
     * d'une reservation : evite les doublons en cas de relance partielle (Tour 12bis, finding C2).
     * Hibernate ajoute automatiquement {@code AND hotel_id = ?} via {@code @TenantId}.
     */
    boolean existsByReservationIdAndChambreIdAndDateNuit(Long reservationId, Long chambreId, java.time.LocalDate dateNuit);

    /** Suppression des nuitees d'une reservation (utilise pour regeneration). */
    @Modifying
    @Query("DELETE FROM Nuitee n WHERE n.reservationId = :reservationId")
    int deleteByReservationId(@Param("reservationId") Long reservationId);

    /**
     * Page des nuitees d'une chambre (Tour 14 - NuiteeController). Pas de tri
     * implicite : utiliser {@link com.cityprojects.citybackend.common.paging.PageableUtils#stable}
     * au niveau service pour garantir la stabilite (finding I2).
     */
    Page<Nuitee> findByChambreId(Long chambreId, Pageable pageable);

    // ============================================================================
    // Tour 40 — Reporting MVP : agregats pour rapports R-HEB-001 et R-NA-001.
    // Toutes ces requetes operent sur l'entite tenant Nuitee, donc Hibernate
    // ajoute automatiquement WHERE hotel_id = ? via @TenantId.
    // ============================================================================

    /**
     * Compte les nuitees occupees (CONSOMMEE + FACTUREE) sur la plage [from, to)
     * groupes par {@code typeId} de la chambre (R-HEB-001).
     *
     * <p>Jointure sur {@code Chambre} pour recuperer le type via {@code chambreId}.</p>
     */
    @Query("SELECT c.typeId AS typeId, COUNT(n) AS nbNuiteesOccupees "
            + "FROM Nuitee n, com.cityprojects.citybackend.entity.hebergement.Chambre c "
            + "WHERE n.chambreId = c.chambreId "
            + "AND n.dateNuit >= :from AND n.dateNuit < :to "
            + "AND n.statut IN (com.cityprojects.citybackend.entity.hebergement.StatutNuitee.CONSOMMEE, "
            + "                 com.cityprojects.citybackend.entity.hebergement.StatutNuitee.FACTUREE) "
            + "GROUP BY c.typeId")
    List<NuiteeOccupationProjection> aggregateOccupationByType(@Param("from") LocalDate from,
                                                               @Param("to") LocalDate to);

    /**
     * Compte les nuitees totales (tous statuts) sur la plage [from, to) (R-HEB-001 totaux).
     */
    @Query("SELECT COUNT(n) FROM Nuitee n "
            + "WHERE n.dateNuit >= :from AND n.dateNuit < :to "
            + "AND n.statut IN (com.cityprojects.citybackend.entity.hebergement.StatutNuitee.CONSOMMEE, "
            + "                 com.cityprojects.citybackend.entity.hebergement.StatutNuitee.FACTUREE)")
    long countOccupeesOnRange(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * Compte les nuitees CONSOMMEE pour une date donnee (R-NA-001).
     */
    @Query("SELECT COUNT(n) FROM Nuitee n WHERE n.dateNuit = :date "
            + "AND n.statut = com.cityprojects.citybackend.entity.hebergement.StatutNuitee.CONSOMMEE")
    long countConsommeesByDate(@Param("date") LocalDate date);

    /**
     * Compte toutes les nuitees creees pour une date donnee (R-NA-001 - nuitees generees).
     */
    @Query("SELECT COUNT(n) FROM Nuitee n WHERE n.dateNuit = :date")
    long countByDateNuit(@Param("date") LocalDate date);
}

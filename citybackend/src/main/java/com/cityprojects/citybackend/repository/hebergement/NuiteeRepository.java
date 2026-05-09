package com.cityprojects.citybackend.repository.hebergement;

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
}

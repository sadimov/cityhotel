package com.cityprojects.citybackend.repository.hebergement;

import com.cityprojects.citybackend.entity.hebergement.EtatJourneeHoteliere;
import com.cityprojects.citybackend.entity.hebergement.JourneeHoteliere;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repository pour {@link JourneeHoteliere}.
 *
 * <p>Toutes les méthodes paginées sur le tenant courant sont filtrées
 * automatiquement par Hibernate via {@code @TenantId} : pas besoin d'ajouter
 * {@code AND hotel_id = :hotelId} dans les JPQL.</p>
 *
 * <p>Exception : {@link #findActiveForHotel(Long, List)} bypass volontairement
 * le filtre tenant pour permettre au {@code NightAuditLockFilter} et au boot
 * de l'app de lire l'état des journées de TOUS les hôtels (mode ROOT via
 * {@code TenantScope.runAsRoot}).</p>
 */
@Repository
public interface JourneeHoteliereRepository extends JpaRepository<JourneeHoteliere, Long> {

    /**
     * Retourne la journée active (OUVERTE ou CLOTURE_EN_COURS) du tenant
     * courant. Tenant filter Hibernate applique : ne voit que les journées
     * de l'hôtel positionné dans {@code TenantContext}.
     *
     * <p>Au plus une ligne par contrat (UNIQUE PARTIAL Postgres + garde
     * applicative). En cas d'incohérence (deux journées OUVERTES, situation
     * anormale), le premier ordre par {@code id DESC} retourne la plus récente.</p>
     */
    @Query("""
            SELECT j FROM JourneeHoteliere j
            WHERE j.etat IN (
                com.cityprojects.citybackend.entity.hebergement.EtatJourneeHoteliere.OUVERTE,
                com.cityprojects.citybackend.entity.hebergement.EtatJourneeHoteliere.CLOTURE_EN_COURS)
            ORDER BY j.id DESC
            """)
    List<JourneeHoteliere> findActiveCurrentTenant();

    /**
     * Verrou pessimiste pour les transitions startClosure / completeClosure
     * (évite race entre 2 admins). Combiné avec {@code @Version}, donne une
     * garantie forte sur l'unicité de la transition.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT j FROM JourneeHoteliere j
            WHERE j.id = :id
            """)
    Optional<JourneeHoteliere> findByIdForUpdate(@Param("id") Long id);

    Optional<JourneeHoteliere> findByDateHotel(LocalDate dateHotel);

    /**
     * Lecture cross-tenant des journées actives, utilisée par le
     * {@code NightAuditLockFilter} au démarrage de l'app (rebuild de l'état
     * en mémoire) et par les diagnostics.
     *
     * <p><b>Ne PAS appeler depuis un endpoint métier</b> — exclusivement
     * depuis un contexte ROOT ({@code TenantScope.runAsRoot}). La méthode est
     * une projection native qui ne dépend pas du tenant filter, et lit la
     * colonne {@code hotel_id} explicitement.</p>
     */
    @Query(value = """
            SELECT j.hotel_id AS hotelId, j.etat AS etat
            FROM hebergement.journee_hoteliere j
            WHERE j.etat IN ('OUVERTE', 'CLOTURE_EN_COURS')
            """, nativeQuery = true)
    List<ActiveDayProjection> findAllActiveCrossTenant();

    /** Projection pour {@link #findAllActiveCrossTenant()}. */
    interface ActiveDayProjection {
        Long getHotelId();
        String getEtat();
    }
}

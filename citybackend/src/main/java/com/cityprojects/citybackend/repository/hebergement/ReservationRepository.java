package com.cityprojects.citybackend.repository.hebergement;

import com.cityprojects.citybackend.entity.hebergement.Reservation;
import com.cityprojects.citybackend.entity.hebergement.StatutReservation;
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
 * Repository des reservations.
 *
 * <p>Hibernate ajoute automatiquement {@code WHERE hotel_id = ?} via
 * {@link org.hibernate.annotations.TenantId}. Les methodes N'ACCEPTENT donc
 * PAS de parametre {@code hotelId} : utiliser le {@link com.cityprojects.citybackend.common.tenant.TenantContext}.</p>
 */
@Repository
public interface ReservationRepository
        extends JpaRepository<Reservation, Long>, JpaSpecificationExecutor<Reservation> {

    /** Recherche par numero metier (tenant courant). */
    Optional<Reservation> findByNumeroReservation(String numeroReservation);

    /** Test d'unicite du numero (tenant courant). */
    boolean existsByNumeroReservation(String numeroReservation);

    /** Page des reservations d'un statut, ordonnees par date d'arrivee descendante. */
    Page<Reservation> findByStatutOrderByDateArriveeDesc(StatutReservation statut, Pageable pageable);

    /** Page des reservations d'un client, plus recentes d'abord. */
    Page<Reservation> findByClientPrincipalIdOrderByDateArriveeDesc(Long clientPrincipalId, Pageable pageable);

    /** Reservations en cours a une date donnee (CHECKED-IN englobant la date). */
    List<Reservation> findByStatutAndDateArriveeLessThanEqualAndDateDepartGreaterThan(
            StatutReservation statut, LocalDate aujourd, LocalDate aujourd2);

    /**
     * Reservations dans un statut donne dont la dateArrivee est strictement
     * anterieure a la date fournie. Utilise par le night audit (Tour 13) pour
     * detecter les CONFIRMEE non honorees a transformer en NO_SHOW.
     *
     * <p>Hibernate ajoute automatiquement {@code AND hotel_id = ?} via {@code @TenantId}.</p>
     */
    List<Reservation> findByStatutAndDateArriveeBefore(StatutReservation statut, LocalDate date);

    /**
     * Reservations dans un statut donne (typiquement {@code ARRIVEE}, sejour en cours).
     * Tour 13 - utilise pour generer les nuitees manquantes en cas de trou.
     *
     * <p>Hibernate ajoute automatiquement {@code AND hotel_id = ?} via {@code @TenantId}.</p>
     */
    List<Reservation> findByStatut(StatutReservation statut);

    /**
     * Variante paginee (Tour 14 audit, finding I2). Permet d'appliquer un tri
     * stable depuis le service via {@link Pageable#getSort()} sans tri implicite
     * du nom de methode.
     */
    Page<Reservation> findByStatut(StatutReservation statut, Pageable pageable);

    /**
     * Reservations dont la {@code dateArrivee} = date donnee, filtrees sur un
     * statut. Utilise par {@code arrivees-today} (Tour 14 B2 API).
     */
    List<Reservation> findByDateArriveeAndStatutOrderByDateArriveeAsc(LocalDate date, StatutReservation statut);

    /**
     * Reservations dont la {@code dateDepart} = date donnee, filtrees sur un
     * statut. Utilise par {@code departs-today}.
     */
    List<Reservation> findByDateDepartAndStatutOrderByDateDepartAsc(LocalDate date, StatutReservation statut);

    /**
     * Reservations dont la {@code dateArrivee} est strictement anterieure a la
     * date fournie, filtrees sur un statut. Utilise par {@code check-ins-retard}.
     */
    List<Reservation> findByStatutAndDateArriveeBeforeOrderByDateArriveeAsc(
            StatutReservation statut, LocalDate date);

    /**
     * Recherche libre (Tour 14 B2 API) : LIKE insensible a la casse sur
     * {@code numero_reservation} OU sur le nom / prenom / telephone du client
     * principal (joint avec {@code Client} via {@code clientPrincipalId}).
     *
     * <p>La jointure passe par le repository de {@code Client} pour rester
     * coherent avec le filtre tenant Hibernate des deux entites (le filtre
     * {@code @TenantId} est applique a Reservation ET a Client par Hibernate).</p>
     */
    @Query("SELECT r FROM Reservation r, com.cityprojects.citybackend.entity.client.Client c "
            + "WHERE c.clientId = r.clientPrincipalId AND ("
            + " LOWER(r.numeroReservation) LIKE LOWER(CONCAT('%', :terme, '%'))"
            + " OR LOWER(c.nom) LIKE LOWER(CONCAT('%', :terme, '%'))"
            + " OR LOWER(c.prenom) LIKE LOWER(CONCAT('%', :terme, '%'))"
            + " OR (c.telephone IS NOT NULL AND LOWER(c.telephone) LIKE LOWER(CONCAT('%', :terme, '%')))"
            + ")")
    Page<Reservation> rechercher(@Param("terme") String terme, Pageable pageable);
}

package com.cityprojects.citybackend.repository.hebergement;

import com.cityprojects.citybackend.dto.reporting.projection.AlosByTypeProjection;
import com.cityprojects.citybackend.dto.reporting.projection.ReservationSourceProjection;
import com.cityprojects.citybackend.entity.hebergement.Reservation;
import com.cityprojects.citybackend.entity.hebergement.StatutReservation;
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

    // ============================================================================
    // Tour 40 — Reporting MVP : agregats pour R-NA-001 (recap night audit).
    // Le filtre tenant est ajoute automatiquement par Hibernate via @TenantId.
    // ============================================================================

    /**
     * Compte les reservations actives a une date (CONFIRMEE OR ARRIVEE englobant la date) (R-NA-001).
     */
    @Query("SELECT COUNT(r) FROM Reservation r "
            + "WHERE r.dateArrivee <= :date AND r.dateDepart > :date "
            + "AND r.statut IN ("
            + " com.cityprojects.citybackend.entity.hebergement.StatutReservation.CONFIRMEE, "
            + " com.cityprojects.citybackend.entity.hebergement.StatutReservation.ARRIVEE)")
    long countActivesAtDate(@Param("date") java.time.LocalDate date);

    /**
     * Compte les reservations passees a NO_SHOW dont la dateArrivee = date (R-NA-001).
     */
    @Query("SELECT COUNT(r) FROM Reservation r "
            + "WHERE r.dateArrivee = :date "
            + "AND r.statut = com.cityprojects.citybackend.entity.hebergement.StatutReservation.NO_SHOW")
    long countNoShowOnDate(@Param("date") java.time.LocalDate date);

    // ============================================================================
    // Tour 41 — Reporting P1/P2 : agregats pour R-HEB-002 / 003 / 004 / 005.
    // Le filtre tenant est ajoute automatiquement par Hibernate via @TenantId.
    // ============================================================================

    /**
     * ALOS global (R-HEB-002) : somme nb_nuits / count reservations sur la plage.
     * Plage = reservations dont {@code dateArrivee} est dans [from, to). Exclut ANNULEE/NO_SHOW.
     */
    @Query("SELECT COUNT(r), COALESCE(SUM(r.nbNuits), 0) FROM Reservation r "
            + "WHERE r.dateArrivee >= :from AND r.dateArrivee < :to "
            + "AND r.statut NOT IN ("
            + "  com.cityprojects.citybackend.entity.hebergement.StatutReservation.ANNULEE, "
            + "  com.cityprojects.citybackend.entity.hebergement.StatutReservation.NO_SHOW)")
    Object[] aggregateAlosGlobal(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * ALOS par type de chambre (R-HEB-002). Jointure via le premier
     * {@code ReservationChambre} (groupage simple : on prend le typeId de la
     * 1ere chambre rattachee).
     */
    @Query("SELECT t.typeCode AS typeCode, t.typeNom AS typeNom, "
            + "  COUNT(DISTINCT r) AS nbReservations, "
            + "  COALESCE(SUM(r.nbNuits), 0) AS totalNuits "
            + "FROM Reservation r, "
            + "  com.cityprojects.citybackend.entity.hebergement.ReservationChambre rc, "
            + "  com.cityprojects.citybackend.entity.hebergement.Chambre c, "
            + "  com.cityprojects.citybackend.entity.hebergement.TypeChambre t "
            + "WHERE rc.reservationId = r.reservationId "
            + "AND rc.chambreId = c.chambreId "
            + "AND c.typeId = t.typeId "
            + "AND r.dateArrivee >= :from AND r.dateArrivee < :to "
            + "AND r.statut NOT IN ("
            + "  com.cityprojects.citybackend.entity.hebergement.StatutReservation.ANNULEE, "
            + "  com.cityprojects.citybackend.entity.hebergement.StatutReservation.NO_SHOW) "
            + "GROUP BY t.typeCode, t.typeNom "
            + "ORDER BY t.typeCode ASC")
    List<AlosByTypeProjection> aggregateAlosByType(@Param("from") LocalDate from,
                                                   @Param("to") LocalDate to);

    /**
     * Tout reservation (avec dateArrivee et statut) dans la plage [from, to)
     * pour calculer no-show + ALOS par mois cote service (groupage en memoire).
     */
    @Query("SELECT r FROM Reservation r WHERE r.dateArrivee >= :from AND r.dateArrivee < :to")
    List<Reservation> findAllArrivantBetween(@Param("from") LocalDate from,
                                             @Param("to") LocalDate to);

    /**
     * Compte les reservations NO_SHOW + total sur la plage (R-HEB-003 global).
     * Retourne {@code [totalReservations, nbNoShow]}.
     */
    @Query("SELECT COUNT(r), "
            + "  SUM(CASE WHEN r.statut = com.cityprojects.citybackend.entity.hebergement.StatutReservation.NO_SHOW THEN 1 ELSE 0 END) "
            + "FROM Reservation r "
            + "WHERE r.dateArrivee >= :from AND r.dateArrivee < :to")
    Object[] aggregateNoShowGlobal(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * Repartition reservations par {@code source_canal} (R-HEB-004) sur la plage
     * (hors ANNULEE). NULL conserve - le service le remplace par "NON_RENSEIGNE".
     */
    @Query("SELECT r.sourceCanal AS sourceCanal, "
            + "  COUNT(r) AS nbReservations, "
            + "  COALESCE(SUM(r.montantTotal), 0) AS caMontant "
            + "FROM Reservation r "
            + "WHERE r.dateArrivee >= :from AND r.dateArrivee < :to "
            + "AND r.statut <> com.cityprojects.citybackend.entity.hebergement.StatutReservation.ANNULEE "
            + "GROUP BY r.sourceCanal "
            + "ORDER BY COUNT(r) DESC")
    List<ReservationSourceProjection> aggregateBySourceCanal(@Param("from") LocalDate from,
                                                             @Param("to") LocalDate to);

    /** Somme {@code montantTotal} total sur la plage (informationnel R-HEB-004). */
    @Query("SELECT COALESCE(SUM(r.montantTotal), 0) FROM Reservation r "
            + "WHERE r.dateArrivee >= :from AND r.dateArrivee < :to "
            + "AND r.statut <> com.cityprojects.citybackend.entity.hebergement.StatutReservation.ANNULEE")
    BigDecimal sumMontantTotalOnRange(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * Compte les check-in d'une date donnee (R-HEB-005). Approxime par
     * {@code dateArrivee = :date AND statut IN (ARRIVEE, PARTIE)} - une fois
     * que le check-in a eu lieu, le statut transite ARRIVEE puis PARTIE.
     */
    @Query("SELECT COUNT(r) FROM Reservation r "
            + "WHERE r.dateArrivee = :date "
            + "AND r.statut IN ("
            + "  com.cityprojects.citybackend.entity.hebergement.StatutReservation.ARRIVEE, "
            + "  com.cityprojects.citybackend.entity.hebergement.StatutReservation.PARTIE)")
    long countCheckInOnDate(@Param("date") LocalDate date);

    /**
     * Compte les check-out d'une date donnee (R-HEB-005). Approxime par
     * {@code dateDepart = :date AND statut = PARTIE} - la transition vers
     * PARTIE marque le check-out.
     */
    @Query("SELECT COUNT(r) FROM Reservation r "
            + "WHERE r.dateDepart = :date "
            + "AND r.statut = com.cityprojects.citybackend.entity.hebergement.StatutReservation.PARTIE")
    long countCheckOutOnDate(@Param("date") LocalDate date);

    /**
     * Compte les walk-in d'une date donnee (R-HEB-005) : reservations creees
     * ET arrivees le meme jour. Critere base sur {@code createdAt} (audit) :
     * cast en date par la BDD ne marche pas portable, on filtre cote service
     * apres recuperation des candidats.
     */
    @Query("SELECT r FROM Reservation r WHERE r.dateArrivee = :date")
    List<Reservation> findArriveesOnDate(@Param("date") LocalDate date);
}

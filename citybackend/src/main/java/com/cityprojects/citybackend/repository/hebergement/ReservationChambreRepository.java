package com.cityprojects.citybackend.repository.hebergement;

import com.cityprojects.citybackend.entity.hebergement.ReservationChambre;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/**
 * Repository du pivot reservation-chambre.
 *
 * <p>Hibernate ajoute automatiquement {@code WHERE hotel_id = ?} via
 * {@link org.hibernate.annotations.TenantId}.</p>
 */
@Repository
public interface ReservationChambreRepository
        extends JpaRepository<ReservationChambre, Long>, JpaSpecificationExecutor<ReservationChambre> {

    /** Liste les chambres rattachees a une reservation. */
    List<ReservationChambre> findByReservationIdOrderByDateDebutAsc(Long reservationId);

    /**
     * Liste batch les pivots pour plusieurs reservations. Utilise pour
     * enrichir une page de {@code ReservationDto} sans tomber en N+1.
     */
    List<ReservationChambre> findByReservationIdInOrderByDateDebutAsc(Collection<Long> reservationIds);

    /**
     * Conflits sur une chambre : retourne les pivots qui chevauchent la periode
     * [{@code dateDebut}, {@code dateFin}] pour la {@code chambreId} donnee
     * (tenant courant).
     */
    @Query("SELECT rc FROM ReservationChambre rc WHERE rc.chambreId = :chambreId "
            + "AND rc.dateDebut < :dateFin AND rc.dateFin > :dateDebut")
    List<ReservationChambre> findConflicts(@Param("chambreId") Long chambreId,
                                            @Param("dateDebut") LocalDate dateDebut,
                                            @Param("dateFin") LocalDate dateFin);

    /**
     * Variante de {@link #findConflicts(Long, LocalDate, LocalDate)} avec
     * verrou pessimiste exclusif (defense en profondeur double-booking,
     * Tour 12bis finding C1). Doit etre invoque dans une transaction
     * en ecriture (cf. {@code ReservationServiceImpl#create}). Postgres
     * complete la defense via {@code EXCLUDE USING gist} (changeset 004).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT rc FROM ReservationChambre rc WHERE rc.chambreId = :chambreId "
            + "AND rc.dateDebut < :dateFin AND rc.dateFin > :dateDebut")
    List<ReservationChambre> findConflictsForUpdate(@Param("chambreId") Long chambreId,
                                                     @Param("dateDebut") LocalDate dateDebut,
                                                     @Param("dateFin") LocalDate dateFin);

    /** Suppression des chambres d'une reservation (utilise pour modification). */
    @Modifying
    @Query("DELETE FROM ReservationChambre rc WHERE rc.reservationId = :reservationId")
    int deleteByReservationId(@Param("reservationId") Long reservationId);
}

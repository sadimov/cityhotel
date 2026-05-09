package com.cityprojects.citybackend.repository.menage;

import com.cityprojects.citybackend.entity.menage.Planning;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Repository des creneaux de planning du personnel de menage.
 *
 * <p>Filtre tenant {@code WHERE hotel_id = ?} ajoute automatiquement par
 * Hibernate via {@link org.hibernate.annotations.TenantId}.</p>
 */
@Repository
public interface PlanningRepository extends JpaRepository<Planning, Long> {

    /** Plannings d'un personnel pour une date triees par heure de debut. */
    List<Planning> findByPersonnelIdAndDateTravailOrderByHeureDebutAsc(
            Long personnelId, LocalDate dateTravail);

    /** Plannings d'une date triees par personnel puis heure de debut. */
    List<Planning> findByDateTravailOrderByPersonnelIdAscHeureDebutAsc(LocalDate dateTravail);

    /** Compteur creneaux disponibles pour une date (utilise par PersonnelService). */
    long countByDateTravailAndDisponibleTrue(LocalDate dateTravail);

    /** Liste des creneaux disponibles pour une date. */
    List<Planning> findByDateTravailAndDisponibleTrueOrderByPersonnelIdAscHeureDebutAsc(
            LocalDate dateTravail);

    /**
     * Compte les chevauchements de creneau pour un meme personnel sur une meme
     * date. Exclut le {@code planningIdExclu} (utilise au update pour autoriser
     * la mise a jour de l'enregistrement courant).
     */
    @Query("SELECT COUNT(p) FROM Planning p WHERE p.personnelId = :personnelId "
            + "AND p.dateTravail = :date "
            + "AND p.planningId <> :planningIdExclu "
            + "AND NOT (p.heureFin <= :heureDebut OR p.heureDebut >= :heureFin)")
    long countConflits(@Param("personnelId") Long personnelId,
                       @Param("date") LocalDate date,
                       @Param("heureDebut") LocalTime heureDebut,
                       @Param("heureFin") LocalTime heureFin,
                       @Param("planningIdExclu") Long planningIdExclu);
}

package com.cityprojects.citybackend.repository.finance;

import com.cityprojects.citybackend.entity.finance.Exercice;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Repository de l'exercice comptable.
 *
 * <p>Tenant-aware : Hibernate ajoute automatiquement le filtre
 * {@code WHERE hotel_id = ?} via {@code @TenantId} sur {@link Exercice}.</p>
 */
@Repository
public interface ExerciceRepository extends JpaRepository<Exercice, Long> {

    /**
     * Recupere l'exercice contenant une date donnee (bornes inclusives).
     * Utilise pour la garde anti-modification d'un exercice clos.
     */
    @Query("SELECT e FROM Exercice e "
            + "WHERE :date BETWEEN e.dateDebut AND e.dateFin")
    Optional<Exercice> findContainingDate(@Param("date") LocalDate date);

    /**
     * Verrou pessimiste sur la lecture d'un exercice par date - utilise pour
     * serialiser l'auto-creation concurrente de l'exercice courant.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Exercice e "
            + "WHERE :date BETWEEN e.dateDebut AND e.dateFin")
    Optional<Exercice> findContainingDateForUpdate(@Param("date") LocalDate date);

    Optional<Exercice> findByCode(String code);

    Page<Exercice> findAllByOrderByDateDebutDesc(Pageable pageable);
}

package com.cityprojects.citybackend.repository.finance;

import com.cityprojects.citybackend.entity.finance.NumerotationSequence;
import com.cityprojects.citybackend.service.finance.TypeNumerotation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository pour les compteurs de numerotation comptable.
 * <p>
 * Le filtre {@code WHERE hotel_id = ?} est ajoute automatiquement par
 * Hibernate via {@link org.hibernate.annotations.TenantId} sur le champ
 * {@code hotelId} de {@link NumerotationSequence}. Aucun parametre
 * {@code hotelId} n'est donc passe explicitement dans les methodes ci-dessous.
 * <p>
 * Cle primaire technique {@code Long id} (cf. doc de l'entite : la PK
 * composite hotel/type/exercice est interdite par Hibernate 6 sur un champ
 * @TenantId, on a donc une cle technique + un UNIQUE INDEX metier).
 */
@Repository
public interface NumerotationSequenceRepository
        extends JpaRepository<NumerotationSequence, Long> {

    /**
     * Recupere le compteur actif pour le couple (type, exercice) en posant un
     * verrou pessimiste en ecriture.
     * <p>
     * Comportement attendu :
     * <ul>
     *   <li>Postgres traduit ce lock en {@code SELECT ... FOR UPDATE} : les
     *       transactions concurrentes sont bloquees jusqu'au COMMIT/ROLLBACK
     *       de la transaction proprietaire du verrou.</li>
     *   <li>H2 implemente egalement un verrou compatible en mode
     *       {@code MODE=PostgreSQL} (sequentialise les acces).</li>
     * </ul>
     * Combine avec @Transactional et le filtre auto par tenant, c'est le
     * mecanisme qui garantit l'unicite de {@code last_value} en concurrence.
     *
     * @param type     famille de numerotation
     * @param exercice annee comptable
     * @return le compteur ou {@code Optional.empty()} s'il n'existe pas encore
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT n FROM NumerotationSequence n WHERE n.type = :type AND n.exercice = :exercice")
    Optional<NumerotationSequence> findByTypeAndExerciceForUpdate(
            @Param("type") TypeNumerotation type,
            @Param("exercice") Integer exercice);
}

package com.cityprojects.citybackend.repository.finance;

import com.cityprojects.citybackend.entity.finance.EcritureComptable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Repository de l'ecriture comptable.
 *
 * <p>Tenant-aware : Hibernate ajoute automatiquement le filtre
 * {@code WHERE hotel_id = ?} via {@code @TenantId} sur l'entite
 * {@link EcritureComptable}. Pas de parametre {@code hotelId} explicite.</p>
 */
@Repository
public interface EcritureComptableRepository extends JpaRepository<EcritureComptable, Long> {

    /** Recherche par numero (unique par hotel). */
    Optional<EcritureComptable> findByNumero(String numero);

    /** Toutes les ecritures d'un exercice, paginees. */
    Page<EcritureComptable> findByExerciceId(Long exerciceId, Pageable pageable);

    /**
     * Ecritures d'un journal sur une plage de dates comptables (bornes
     * inclusives). Utilise pour les exports / consultations du journal.
     */
    @Query("SELECT e FROM EcritureComptable e "
            + "WHERE e.journal.id = :journalId "
            + "AND e.dateComptable BETWEEN :dateDebut AND :dateFin")
    Page<EcritureComptable> findByJournalIdAndDateBetween(
            @Param("journalId") Long journalId,
            @Param("dateDebut") LocalDate dateDebut,
            @Param("dateFin") LocalDate dateFin,
            Pageable pageable);

    /**
     * Ecritures qui contiennent au moins une ligne sur un compte donne, sur
     * une plage de dates comptables. Utilise pour le grand livre.
     *
     * <p>Le filtre {@code WHERE hotel_id = ?} est applique deux fois par
     * Hibernate (sur EcritureComptable ET sur LigneEcriture), ce qui garantit
     * l'isolation meme si l'un des cotes etait compromis.</p>
     */
    @Query("SELECT DISTINCT e FROM EcritureComptable e "
            + "JOIN e.lignes l "
            + "WHERE l.compteCode = :compteCode "
            + "AND e.dateComptable BETWEEN :dateDebut AND :dateFin")
    Page<EcritureComptable> findByCompteCodeAndDateBetween(
            @Param("compteCode") String compteCode,
            @Param("dateDebut") LocalDate dateDebut,
            @Param("dateFin") LocalDate dateFin,
            Pageable pageable);
}

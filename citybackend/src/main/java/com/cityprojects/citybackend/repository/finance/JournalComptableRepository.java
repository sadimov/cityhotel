package com.cityprojects.citybackend.repository.finance;

import com.cityprojects.citybackend.entity.finance.JournalComptable;
import com.cityprojects.citybackend.entity.finance.TypeJournal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository du journal comptable (par hotel).
 *
 * <p>Tenant-aware : Hibernate ajoute automatiquement le filtre
 * {@code WHERE hotel_id = ?} via {@code @TenantId} sur l'entite
 * {@link JournalComptable}. Aucune methode n'expose le {@code hotelId}.</p>
 */
@Repository
public interface JournalComptableRepository extends JpaRepository<JournalComptable, Long> {

    /** Recupere un journal par son code (ex. {@code "VTE"}). */
    Optional<JournalComptable> findByCode(String code);

    /** Liste des journaux actifs, tries par code croissant. */
    List<JournalComptable> findByActifTrueOrderByCodeAsc();

    /** Liste des journaux d'un type donne, tries par code croissant. */
    List<JournalComptable> findByTypeOrderByCodeAsc(TypeJournal type);

    /** Liste complete triee par code (actifs + inactifs). */
    List<JournalComptable> findAllByOrderByCodeAsc();

    /** Verifie l'existence d'un journal par code (pour les invariants). */
    boolean existsByCode(String code);
}

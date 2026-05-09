package com.cityprojects.citybackend.repository.menage;

import com.cityprojects.citybackend.entity.menage.Historique;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Repository de l'audit log {@code menage.historique}.
 *
 * <p>Filtre tenant {@code WHERE hotel_id = ?} ajoute automatiquement par
 * Hibernate via {@link org.hibernate.annotations.TenantId}.</p>
 */
@Repository
public interface HistoriqueRepository extends JpaRepository<Historique, Long> {

    /** Historique pagine, plus recent en premier. */
    Page<Historique> findAllByOrderByTimestampActionDesc(Pageable pageable);

    /** Historique d'une tache. */
    List<Historique> findByTacheIdOrderByTimestampActionDesc(Long tacheId);

    /** Historique d'une chambre. */
    List<Historique> findByChambreIdOrderByTimestampActionDesc(Long chambreId);

    /** Historique d'un personnel. */
    List<Historique> findByPersonnelIdOrderByTimestampActionDesc(Long personnelId);

    /**
     * Suppression des entrees plus anciennes qu'une date donnee. Filtre tenant
     * applique par Hibernate via {@link org.hibernate.annotations.TenantId}.
     */
    @Modifying
    @Query("DELETE FROM Historique h WHERE h.timestampAction < :avant")
    int deleteOlderThan(@Param("avant") Instant avant);
}

package com.cityprojects.citybackend.repository.hebergement;

import com.cityprojects.citybackend.entity.hebergement.TarifChambre;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * Repository des tarifs saisonniers de chambres.
 *
 * <p>Hibernate ajoute automatiquement {@code WHERE hotel_id = ?} via
 * {@link org.hibernate.annotations.TenantId}.</p>
 */
@Repository
public interface TarifChambreRepository
        extends JpaRepository<TarifChambre, Long>, JpaSpecificationExecutor<TarifChambre> {

    /** Liste des tarifs actifs d'un type donne (tenant courant). */
    List<TarifChambre> findByTypeIdAndActifTrueOrderByDateDebutAsc(Long typeId);

    /**
     * Tarifs actifs applicables a une date donnee (Tour 44 Phase 1) :
     * {@code dateDebut <= date <= dateFin (ou dateFin IS NULL)}.
     * Tries par {@code priorite DESC} pour selection deterministe en cas de
     * chevauchement de promotions. Le service prend le premier resultat.
     */
    @Query("SELECT t FROM TarifChambre t "
            + "WHERE t.typeId = :typeId "
            + "AND t.actif = TRUE "
            + "AND t.dateDebut <= :date "
            + "AND (t.dateFin IS NULL OR t.dateFin >= :date) "
            + "ORDER BY t.priorite DESC, t.dateDebut DESC")
    List<TarifChambre> findApplicableTarifs(@Param("typeId") Long typeId,
                                             @Param("date") LocalDate date);
}

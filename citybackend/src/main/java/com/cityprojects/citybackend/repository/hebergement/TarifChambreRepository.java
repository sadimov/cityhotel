package com.cityprojects.citybackend.repository.hebergement;

import com.cityprojects.citybackend.entity.hebergement.TarifChambre;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

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
}

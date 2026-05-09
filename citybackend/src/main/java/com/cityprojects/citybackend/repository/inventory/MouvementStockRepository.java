package com.cityprojects.citybackend.repository.inventory;

import com.cityprojects.citybackend.entity.inventory.MouvementStock;
import com.cityprojects.citybackend.entity.inventory.TypeMouvementStock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * Repository des mouvements de stock (audit trail).
 *
 * <p>Le filtre {@code WHERE hotel_id = ?} est ajoute automatiquement par Hibernate.</p>
 */
@Repository
public interface MouvementStockRepository
        extends JpaRepository<MouvementStock, Long>, JpaSpecificationExecutor<MouvementStock> {

    /** Page des mouvements pour un produit donne. */
    Page<MouvementStock> findByProduitIdOrderByCreatedAtDesc(Long produitId, Pageable pageable);

    /** Page des mouvements d'un type donne (tenant courant). */
    Page<MouvementStock> findByTypeMouvementOrderByCreatedAtDesc(TypeMouvementStock typeMouvement,
                                                                  Pageable pageable);
}

package com.cityprojects.citybackend.service.inventory;

import com.cityprojects.citybackend.dto.inventory.MouvementStockDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service consultatif des mouvements de stock (audit trail).
 *
 * <p>Pas de creation directe ici : les mouvements sont generes par les services
 * {@link ProduitService} (ajustement), {@link BonCommandeService} (reception),
 * {@link BonSortieService} (livraison).</p>
 */
public interface MouvementStockService {

    Page<MouvementStockDto> findByProduit(Long produitId, Pageable pageable);

    Page<MouvementStockDto> findAll(Pageable pageable);
}

package com.cityprojects.citybackend.repository.restaurant;

import com.cityprojects.citybackend.entity.restaurant.LigneCommande;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository des lignes de commande POS (Tour 24).
 *
 * <p>Filtre tenant automatique via Hibernate {@code @TenantId}.</p>
 */
@Repository
public interface LigneCommandeRepository extends JpaRepository<LigneCommande, Long> {

    /** Lignes d'une commande, dans l'ordre d'insertion (ligne_id ASC). */
    List<LigneCommande> findByCommandeIdOrderByLigneIdAsc(Long commandeId);
}

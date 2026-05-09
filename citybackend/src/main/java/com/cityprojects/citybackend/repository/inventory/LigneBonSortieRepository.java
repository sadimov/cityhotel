package com.cityprojects.citybackend.repository.inventory;

import com.cityprojects.citybackend.entity.inventory.LigneBonSortie;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository des lignes de bons de sortie.
 *
 * <p>Pas de {@code @TenantId} sur cette entite : isolation via la FK
 * {@code bon_sortie_id}. Cf. {@link LigneBonCommandeRepository} pour les details.</p>
 */
@Repository
public interface LigneBonSortieRepository extends JpaRepository<LigneBonSortie, Long> {

    /** Lignes d'un BS, ordonnees par identifiant croissant. */
    List<LigneBonSortie> findByBonSortieIdOrderByLigneIdAsc(Long bonSortieId);
}

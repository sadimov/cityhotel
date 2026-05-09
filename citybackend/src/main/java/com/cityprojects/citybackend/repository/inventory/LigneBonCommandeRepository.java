package com.cityprojects.citybackend.repository.inventory;

import com.cityprojects.citybackend.entity.inventory.LigneBonCommande;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository des lignes de bons de commande.
 *
 * <p>Pas de {@code @TenantId} sur cette entite : la ligne herite l'isolation
 * via la FK {@code bon_commande_id}. La requete sur {@code bonCommandeId} est
 * donc executee sans filtre tenant - le service appelant doit avoir prealablement
 * verifie que le BC appartient au tenant courant via {@code BonCommandeRepository.findById}
 * (qui applique le filtre tenant Hibernate).</p>
 */
@Repository
public interface LigneBonCommandeRepository extends JpaRepository<LigneBonCommande, Long> {

    /** Lignes d'un BC, ordonnees par identifiant croissant. */
    List<LigneBonCommande> findByBonCommandeIdOrderByLigneIdAsc(Long bonCommandeId);
}

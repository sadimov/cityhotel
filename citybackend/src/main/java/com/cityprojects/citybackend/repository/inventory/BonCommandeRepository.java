package com.cityprojects.citybackend.repository.inventory;

import com.cityprojects.citybackend.entity.inventory.BonCommande;
import com.cityprojects.citybackend.entity.inventory.StatutBonCommande;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository des bons de commande.
 *
 * <p>Le filtre {@code WHERE hotel_id = ?} est ajoute automatiquement par Hibernate.</p>
 */
@Repository
public interface BonCommandeRepository
        extends JpaRepository<BonCommande, Long>, JpaSpecificationExecutor<BonCommande> {

    /** Recherche par numero metier (tenant courant). */
    Optional<BonCommande> findByNumeroBc(String numeroBc);

    /** Test d'unicite du numero (tenant courant). */
    boolean existsByNumeroBc(String numeroBc);

    /** Page des BC d'un statut donne, plus recents d'abord. */
    Page<BonCommande> findByStatutOrderByDateCommandeDesc(StatutBonCommande statut, Pageable pageable);

    /** Page des BC d'un fournisseur donne (tous statuts), plus recents d'abord. */
    Page<BonCommande> findByFournisseurIdOrderByDateCommandeDesc(Long fournisseurId, Pageable pageable);
}

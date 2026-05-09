package com.cityprojects.citybackend.repository.inventory;

import com.cityprojects.citybackend.entity.inventory.BonSortie;
import com.cityprojects.citybackend.entity.inventory.StatutBonSortie;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository des bons de sortie.
 *
 * <p>Le filtre {@code WHERE hotel_id = ?} est ajoute automatiquement par Hibernate.</p>
 */
@Repository
public interface BonSortieRepository
        extends JpaRepository<BonSortie, Long>, JpaSpecificationExecutor<BonSortie> {

    /** Recherche par numero metier (tenant courant). */
    Optional<BonSortie> findByNumeroBs(String numeroBs);

    /** Test d'unicite du numero (tenant courant). */
    boolean existsByNumeroBs(String numeroBs);

    /** Page des BS d'un statut donne, plus recents d'abord. */
    Page<BonSortie> findByStatutOrderByDateSortieDesc(StatutBonSortie statut, Pageable pageable);

    /** Page des BS d'une destination donnee, plus recents d'abord. */
    Page<BonSortie> findByDestinationOrderByDateSortieDesc(String destination, Pageable pageable);
}

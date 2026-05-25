package com.cityprojects.citybackend.repository.reference;

import com.cityprojects.citybackend.entity.reference.DonneesReferentielles;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository du référentiel global (catégories nationalite, type_identification, ...).
 *
 * <p>Pas de filtre tenant : référentiel partagé entre tous les hôtels.</p>
 */
@Repository
public interface DonneesReferentiellesRepository
        extends JpaRepository<DonneesReferentielles, Long> {

    /**
     * Liste les entrées actives d'une catégorie, triées par ordre d'affichage
     * puis libellé (alphabétique). La cardinalité reste bornée (~250 entrées
     * max par catégorie pour les nationalités), donc pas de pagination.
     */
    List<DonneesReferentielles> findByCategorieAndActifTrueOrderByOrdreAffichageAscLibelleAsc(
            String categorie);
}

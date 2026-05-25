package com.cityprojects.citybackend.service.reference;

import com.cityprojects.citybackend.dto.reference.DonneesReferentiellesDto;

import java.util.List;

/**
 * Service de lecture du référentiel global (nationalités, types
 * d'identification, ...). Pas d'écriture exposée : le référentiel est seedé
 * par Liquibase.
 */
public interface DonneesReferentiellesService {

    /**
     * Retourne les entrées actives d'une catégorie, triées pour l'affichage.
     */
    List<DonneesReferentiellesDto> findByCategorie(String categorie);
}

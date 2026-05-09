package com.cityprojects.citybackend.service.restaurant;

import com.cityprojects.citybackend.dto.restaurant.LigneRecetteDto;
import com.cityprojects.citybackend.dto.restaurant.RecetteArticleCreateDto;
import com.cityprojects.citybackend.dto.restaurant.RecetteArticleDto;

import java.util.List;

/**
 * Service de gestion des recettes d'articles (Tour 25).
 *
 * <p>Toutes les methodes operent dans le tenant courant ; aucun parametre
 * {@code hotelId}.</p>
 */
public interface RecetteArticleService {

    /** Cree une ligne de recette. Verifie article + produit existent (tenant courant). */
    RecetteArticleDto create(RecetteArticleCreateDto dto);

    /** Met a jour la quantite, l'unite et la note. {@code articleId}/{@code produitId} non modifiables. */
    RecetteArticleDto update(Long recetteId, RecetteArticleCreateDto dto);

    /** Soft delete : positionne {@code actif = false}. */
    void delete(Long recetteId);

    /** Recherche par id (tenant courant). */
    RecetteArticleDto findById(Long recetteId);

    /** Lignes ACTIVES de la recette d'un article (utilise par la generation BS auto). */
    List<RecetteArticleDto> findActiveByArticle(Long articleId);

    /** Toutes les lignes de la recette d'un article (admin / UI). */
    List<RecetteArticleDto> findAllByArticle(Long articleId);

    /**
     * Remplace toute la recette d'un article : desactive les lignes existantes
     * (soft delete) et cree les nouvelles fournies. Permet a l'UI de saisir la
     * recette d'un article en bloc.
     */
    List<RecetteArticleDto> setRecetteForArticle(Long articleId, List<LigneRecetteDto> lignes);
}

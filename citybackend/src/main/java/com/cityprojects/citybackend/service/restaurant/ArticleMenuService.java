package com.cityprojects.citybackend.service.restaurant;

import com.cityprojects.citybackend.dto.restaurant.ArticleMenuCreateDto;
import com.cityprojects.citybackend.dto.restaurant.ArticleMenuDto;
import com.cityprojects.citybackend.dto.restaurant.ArticleMenuUpdateDto;
import com.cityprojects.citybackend.entity.restaurant.StatutArticle;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Service de gestion du catalogue des articles de menu (par hotel).
 *
 * <p>Toutes les methodes operent dans le tenant courant ; aucun parametre
 * {@code hotelId}.</p>
 */
public interface ArticleMenuService {

    ArticleMenuDto create(ArticleMenuCreateDto dto);

    ArticleMenuDto update(Long articleId, ArticleMenuUpdateDto dto);

    ArticleMenuDto findById(Long articleId);

    Page<ArticleMenuDto> search(String recherche, Long categorieId, Pageable pageable);

    /**
     * Liste des articles disponibles pour le POS : actifs + statut ACTIF.
     * Filtre optionnel par catégorie. Tri alphabétique sur le nom.
     */
    List<ArticleMenuDto> findDisponibles(Long categorieId);

    /** Change le statut metier (ACTIF / RUPTURE / INACTIF). */
    ArticleMenuDto changeStatut(Long articleId, StatutArticle nouveauStatut);

    void deactivate(Long articleId);

    void reactivate(Long articleId);
}

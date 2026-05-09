package com.cityprojects.citybackend.repository.restaurant;

import com.cityprojects.citybackend.entity.restaurant.RecetteArticle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository des recettes d'articles (Tour 25).
 *
 * <p>Filtre tenant automatique via Hibernate {@code @TenantId}. Les methodes
 * N'ACCEPTENT donc PAS de parametre {@code hotelId}.</p>
 */
@Repository
public interface RecetteArticleRepository
        extends JpaRepository<RecetteArticle, Long>, JpaSpecificationExecutor<RecetteArticle> {

    /** Lignes de recette ACTIVES d'un article (utilise par la generation BS auto). */
    List<RecetteArticle> findByArticleIdAndActifTrue(Long articleId);

    /** Toutes les lignes de recette d'un article (admin / UI). */
    List<RecetteArticle> findByArticleId(Long articleId);

    /** Test d'unicite article+produit. */
    boolean existsByArticleIdAndProduitId(Long articleId, Long produitId);
}

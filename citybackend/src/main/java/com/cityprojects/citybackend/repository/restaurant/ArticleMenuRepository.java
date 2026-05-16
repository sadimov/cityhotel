package com.cityprojects.citybackend.repository.restaurant;

import com.cityprojects.citybackend.entity.restaurant.ArticleMenu;
import com.cityprojects.citybackend.entity.restaurant.StatutArticle;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository des articles de menu (catalogue restaurant).
 *
 * <p>Le filtre {@code WHERE hotel_id = ?} est ajoute automatiquement par
 * Hibernate via {@link org.hibernate.annotations.TenantId}. Les methodes
 * N'ACCEPTENT donc PAS de parametre {@code hotelId}.</p>
 */
@Repository
public interface ArticleMenuRepository
        extends JpaRepository<ArticleMenu, Long>, JpaSpecificationExecutor<ArticleMenu> {

    /** Recherche par code metier (unique par hotel). */
    Optional<ArticleMenu> findByCodeArticle(String codeArticle);

    /** Test d'unicite du code dans le tenant courant. */
    boolean existsByCodeArticle(String codeArticle);

    /** Liste des articles actifs et disponibles d'une categorie (POS). */
    List<ArticleMenu> findByCategorieIdAndActifTrueAndStatutOrderByNomAsc(
            Long categorieId, StatutArticle statut);

    /** Liste de tous les articles actifs et d'un statut donne (POS, toutes categories). */
    List<ArticleMenu> findByActifTrueAndStatutOrderByNomAsc(StatutArticle statut);

    /** Compte les articles actifs lies a une categorie (controle desactivation). */
    long countByCategorieIdAndActifTrue(Long categorieId);

    /** Page filtree par recherche libre + categorie optionnelle. */
    @Query("SELECT a FROM ArticleMenu a WHERE "
            + "(:recherche IS NULL OR :recherche = '' OR "
            + " LOWER(a.nom) LIKE LOWER(CONCAT('%', :recherche, '%')) OR "
            + " LOWER(a.codeArticle) LIKE LOWER(CONCAT('%', :recherche, '%'))) "
            + "AND (:categorieId IS NULL OR a.categorieId = :categorieId)")
    Page<ArticleMenu> search(@Param("recherche") String recherche,
                             @Param("categorieId") Long categorieId,
                             Pageable pageable);
}

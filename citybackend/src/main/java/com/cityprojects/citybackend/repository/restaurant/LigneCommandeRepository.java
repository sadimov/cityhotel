package com.cityprojects.citybackend.repository.restaurant;

import com.cityprojects.citybackend.dto.reporting.projection.TopArticleProjection;
import com.cityprojects.citybackend.entity.restaurant.LigneCommande;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Repository des lignes de commande POS (Tour 24).
 *
 * <p>Filtre tenant automatique via Hibernate {@code @TenantId}.</p>
 */
@Repository
public interface LigneCommandeRepository extends JpaRepository<LigneCommande, Long> {

    /** Lignes d'une commande, dans l'ordre d'insertion (ligne_id ASC). */
    List<LigneCommande> findByCommandeIdOrderByLigneIdAsc(Long commandeId);

    /**
     * Top articles vendus sur la plage [start, end) (R-RES-002 Tour 41).
     * Jointure avec {@code Commande} pour exclure les commandes ANNULEE et
     * filtrer par dateCommande. Hibernate filtre auto par tenant.
     */
    @Query("SELECT l.articleId AS articleId, "
            + "  l.libelle AS libelle, "
            + "  COALESCE(SUM(l.quantite), 0) AS quantiteVendue, "
            + "  COALESCE(SUM(l.montant), 0) AS caTotal "
            + "FROM LigneCommande l, com.cityprojects.citybackend.entity.restaurant.Commande c "
            + "WHERE l.commandeId = c.commandeId "
            + "AND c.dateCommande >= :startInclusive AND c.dateCommande < :endExclusive "
            + "AND c.statut <> com.cityprojects.citybackend.entity.restaurant.StatutCommande.ANNULEE "
            + "GROUP BY l.articleId, l.libelle "
            + "ORDER BY SUM(l.montant) DESC, l.libelle ASC")
    List<TopArticleProjection> findTopArticles(@Param("startInclusive") Instant startInclusive,
                                               @Param("endExclusive") Instant endExclusive,
                                               Pageable pageable);

    /**
     * Toutes les lignes vendues sur la plage [start, end) (R-RES-003 marge).
     * Hibernate filtre auto par tenant.
     */
    @Query("SELECT l FROM LigneCommande l, com.cityprojects.citybackend.entity.restaurant.Commande c "
            + "WHERE l.commandeId = c.commandeId "
            + "AND c.dateCommande >= :startInclusive AND c.dateCommande < :endExclusive "
            + "AND c.statut <> com.cityprojects.citybackend.entity.restaurant.StatutCommande.ANNULEE")
    List<LigneCommande> findLignesOnRange(@Param("startInclusive") Instant startInclusive,
                                          @Param("endExclusive") Instant endExclusive);
}

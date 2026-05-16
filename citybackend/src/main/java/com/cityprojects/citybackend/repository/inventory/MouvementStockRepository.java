package com.cityprojects.citybackend.repository.inventory;

import com.cityprojects.citybackend.dto.reporting.projection.MouvementValoriseProjection;
import com.cityprojects.citybackend.dto.reporting.projection.RotationProduitProjection;
import com.cityprojects.citybackend.entity.inventory.MouvementStock;
import com.cityprojects.citybackend.entity.inventory.TypeMouvementStock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Repository des mouvements de stock (audit trail).
 *
 * <p>Le filtre {@code WHERE hotel_id = ?} est ajoute automatiquement par Hibernate.</p>
 */
@Repository
public interface MouvementStockRepository
        extends JpaRepository<MouvementStock, Long>, JpaSpecificationExecutor<MouvementStock> {

    /** Page des mouvements pour un produit donne. */
    Page<MouvementStock> findByProduitIdOrderByCreatedAtDesc(Long produitId, Pageable pageable);

    /** Existe-t-il au moins un mouvement pour ce produit (tenant courant) ? */
    boolean existsByProduitId(Long produitId);

    /** Page des mouvements d'un type donne (tenant courant). */
    Page<MouvementStock> findByTypeMouvementOrderByCreatedAtDesc(TypeMouvementStock typeMouvement,
                                                                  Pageable pageable);

    // ============================================================================
    // Tour 41 — Reporting P1/P2 : R-INV-002 (valorisation), R-INV-003 (rotation).
    // Filtre tenant ajoute automatiquement par Hibernate via @TenantId.
    // ============================================================================

    /**
     * Mouvements valorises sur la plage [fromInstant, toInstant) avec jointure
     * produit pour acceder a code / nom / prixUnitaire (R-INV-002).
     */
    @Query("SELECT m.mouvementId AS mouvementId, "
            + "  m.createdAt AS date, "
            + "  m.produitId AS produitId, "
            + "  p.codeProduit AS codeProduit, "
            + "  p.nomProduit AS nomProduit, "
            + "  m.typeMouvement AS typeMouvement, "
            + "  m.quantite AS quantite, "
            + "  m.prixUnitaire AS prixUnitaireMouvement, "
            + "  p.prixUnitaire AS prixUnitaireProduit, "
            + "  m.referenceDocument AS referenceDocument "
            + "FROM MouvementStock m, "
            + "  com.cityprojects.citybackend.entity.inventory.Produit p "
            + "WHERE m.produitId = p.produitId "
            + "AND m.createdAt >= :fromInstant AND m.createdAt < :toInstant "
            + "AND (:type IS NULL OR m.typeMouvement = :type) "
            + "ORDER BY m.createdAt DESC")
    List<MouvementValoriseProjection> findValorisesOnRange(@Param("fromInstant") Instant fromInstant,
                                                           @Param("toInstant") Instant toInstant,
                                                           @Param("type") TypeMouvementStock type);

    /**
     * Aggrege les sorties (SORTIE + PERTE) par produit sur la plage (R-INV-003).
     */
    @Query("SELECT p.produitId AS produitId, "
            + "  p.codeProduit AS codeProduit, "
            + "  p.nomProduit AS nomProduit, "
            + "  COALESCE(SUM(m.quantite), 0) AS totalSorties, "
            + "  p.stockActuel AS stockActuel "
            + "FROM MouvementStock m, "
            + "  com.cityprojects.citybackend.entity.inventory.Produit p "
            + "WHERE m.produitId = p.produitId "
            + "AND m.createdAt >= :fromInstant AND m.createdAt < :toInstant "
            + "AND m.typeMouvement IN ("
            + "  com.cityprojects.citybackend.entity.inventory.TypeMouvementStock.SORTIE, "
            + "  com.cityprojects.citybackend.entity.inventory.TypeMouvementStock.PERTE) "
            + "GROUP BY p.produitId, p.codeProduit, p.nomProduit, p.stockActuel "
            + "ORDER BY SUM(m.quantite) DESC")
    List<RotationProduitProjection> aggregateRotation(@Param("fromInstant") Instant fromInstant,
                                                      @Param("toInstant") Instant toInstant);
}

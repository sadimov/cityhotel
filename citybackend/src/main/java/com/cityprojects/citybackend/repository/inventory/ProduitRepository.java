package com.cityprojects.citybackend.repository.inventory;

import com.cityprojects.citybackend.entity.inventory.Produit;
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
 * Repository des produits.
 *
 * <p>Le filtre {@code WHERE hotel_id = ?} est ajoute automatiquement par Hibernate.</p>
 */
@Repository
public interface ProduitRepository
        extends JpaRepository<Produit, Long>, JpaSpecificationExecutor<Produit> {

    /** Liste des produits actifs (tenant courant), ordonnes par nom. */
    List<Produit> findByActifTrueOrderByNomProduitAsc();

    /** Recherche par code unique par hotel. */
    Optional<Produit> findByCodeProduit(String codeProduit);

    /** Test d'unicite du code dans le tenant courant. */
    boolean existsByCodeProduit(String codeProduit);

    /**
     * Produits dont {@code stock_actuel <= seuil_alerte}, actifs uniquement.
     * Cf. {@link Produit#isStockEnAlerte()} (filtre persiste cote SQL).
     */
    @Query("SELECT p FROM Produit p WHERE p.actif = true AND p.stockActuel <= p.seuilAlerte "
            + "ORDER BY p.nomProduit ASC")
    List<Produit> findEnAlerte();

    /**
     * Produits dont {@code stock_actuel <= seuil_critique}, actifs uniquement.
     * Cf. {@link Produit#isStockCritique()}.
     */
    @Query("SELECT p FROM Produit p WHERE p.actif = true AND p.stockActuel <= p.seuilCritique "
            + "ORDER BY p.nomProduit ASC")
    List<Produit> findEnStockCritique();

    /** Page filtree par recherche libre + categorie optionnelle. */
    @Query("SELECT p FROM Produit p WHERE "
            + "(:recherche IS NULL OR :recherche = '' OR "
            + " LOWER(p.nomProduit) LIKE LOWER(CONCAT('%', :recherche, '%')) OR "
            + " LOWER(p.codeProduit) LIKE LOWER(CONCAT('%', :recherche, '%'))) "
            + "AND (:categorieId IS NULL OR p.categorieId = :categorieId)")
    Page<Produit> search(@Param("recherche") String recherche,
                         @Param("categorieId") Long categorieId,
                         Pageable pageable);

    /** Compte le nombre de produits actifs rattaches a une categorie donnee. */
    long countByCategorieIdAndActifTrue(Long categorieId);
}

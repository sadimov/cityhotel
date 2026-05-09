package com.cityprojects.citybackend.repository.inventory;

import com.cityprojects.citybackend.entity.inventory.CategorieProduit;
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
 * Repository des categories de produits.
 *
 * <p>Le filtre {@code WHERE hotel_id = ?} est ajoute automatiquement par Hibernate.</p>
 */
@Repository
public interface CategorieProduitRepository
        extends JpaRepository<CategorieProduit, Long>, JpaSpecificationExecutor<CategorieProduit> {

    /** Liste des categories actives (tenant courant), ordonnees par nom. */
    List<CategorieProduit> findByActifTrueOrderByNomCategorieAsc();

    /** Recherche par code (unique par hotel). */
    Optional<CategorieProduit> findByCodeCategorie(String codeCategorie);

    /** Test d'unicite du code dans le tenant courant. */
    boolean existsByCodeCategorie(String codeCategorie);

    /** Page filtree LIKE sur nom/code. */
    @Query("SELECT c FROM CategorieProduit c WHERE "
            + "(:recherche IS NULL OR :recherche = '' OR "
            + " LOWER(c.nomCategorie) LIKE LOWER(CONCAT('%', :recherche, '%')) OR "
            + " LOWER(c.codeCategorie) LIKE LOWER(CONCAT('%', :recherche, '%')))")
    Page<CategorieProduit> search(@Param("recherche") String recherche, Pageable pageable);
}

package com.cityprojects.citybackend.repository.inventory;

import com.cityprojects.citybackend.entity.inventory.TypeServiceHotelier;
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
 * Repository des types de services hoteliers.
 *
 * <p>Le filtre {@code WHERE hotel_id = ?} est ajoute automatiquement par Hibernate
 * via {@code @TenantId}.</p>
 */
@Repository
public interface TypeServiceHotelierRepository
        extends JpaRepository<TypeServiceHotelier, Long>, JpaSpecificationExecutor<TypeServiceHotelier> {

    /** Liste des types actifs (tenant courant), ordonnes par nom. */
    List<TypeServiceHotelier> findByActifTrueOrderByNomAsc();

    /** Recherche par code unique par hotel. */
    Optional<TypeServiceHotelier> findByCode(String code);

    /** Test d'unicite du code dans le tenant courant. */
    boolean existsByCode(String code);

    /** Page filtree LIKE sur nom/code. */
    @Query("SELECT t FROM TypeServiceHotelier t WHERE "
            + "(:recherche IS NULL OR :recherche = '' OR "
            + " LOWER(t.nom) LIKE LOWER(CONCAT('%', :recherche, '%')) OR "
            + " LOWER(t.code) LIKE LOWER(CONCAT('%', :recherche, '%')))")
    Page<TypeServiceHotelier> search(@Param("recherche") String recherche, Pageable pageable);
}

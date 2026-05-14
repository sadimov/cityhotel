package com.cityprojects.citybackend.repository.inventory;

import com.cityprojects.citybackend.entity.inventory.ServiceHotelier;
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
 * Repository des services hoteliers (prestations facturables au client).
 *
 * <p>Le filtre {@code WHERE hotel_id = ?} est ajoute automatiquement par Hibernate
 * via {@code @TenantId}.</p>
 */
@Repository
public interface ServiceHotelierRepository
        extends JpaRepository<ServiceHotelier, Long>, JpaSpecificationExecutor<ServiceHotelier> {

    /** Liste des services actifs (tenant courant), ordonnes par nom. */
    List<ServiceHotelier> findByActifTrueOrderByNomAsc();

    /** Recherche par code unique par hotel. */
    Optional<ServiceHotelier> findByCode(String code);

    /** Test d'unicite du code dans le tenant courant. */
    boolean existsByCode(String code);

    /** Compte les services actifs rattaches a un type donne. */
    long countByTypeServiceIdAndActifTrue(Long typeServiceId);

    /**
     * Page filtree par recherche libre + type optionnel.
     */
    @Query("SELECT s FROM ServiceHotelier s WHERE "
            + "(:recherche IS NULL OR :recherche = '' OR "
            + " LOWER(s.nom) LIKE LOWER(CONCAT('%', :recherche, '%')) OR "
            + " LOWER(s.code) LIKE LOWER(CONCAT('%', :recherche, '%'))) "
            + "AND (:typeServiceId IS NULL OR s.typeServiceId = :typeServiceId)")
    Page<ServiceHotelier> search(@Param("recherche") String recherche,
                                 @Param("typeServiceId") Long typeServiceId,
                                 Pageable pageable);
}

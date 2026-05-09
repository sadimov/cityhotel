package com.cityprojects.citybackend.repository.restaurant;

import com.cityprojects.citybackend.entity.restaurant.CategorieMenu;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository des categories de menu (catalogue restaurant).
 *
 * <p>Le filtre {@code WHERE hotel_id = ?} est ajoute automatiquement par
 * Hibernate via {@link org.hibernate.annotations.TenantId}. Les methodes
 * N'ACCEPTENT donc PAS de parametre {@code hotelId}.</p>
 */
@Repository
public interface CategorieMenuRepository
        extends JpaRepository<CategorieMenu, Long>, JpaSpecificationExecutor<CategorieMenu> {

    /** Liste des categories actives (tenant courant), triees par ordre puis nom. */
    List<CategorieMenu> findByActifTrueOrderByOrdreAscNomAsc();

    /** Pagination sur le flag actif. */
    Page<CategorieMenu> findByActif(Boolean actif, Pageable pageable);
}

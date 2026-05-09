package com.cityprojects.citybackend.repository.inventory;

import com.cityprojects.citybackend.entity.inventory.Fournisseur;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository des fournisseurs.
 *
 * <p>Le filtre {@code WHERE hotel_id = ?} est ajoute automatiquement par Hibernate
 * via {@link org.hibernate.annotations.TenantId} sur {@link Fournisseur#getHotelId()}.
 * Les methodes N'ACCEPTENT donc PAS de parametre {@code hotelId}.</p>
 */
@Repository
public interface FournisseurRepository
        extends JpaRepository<Fournisseur, Long>, JpaSpecificationExecutor<Fournisseur> {

    /** Liste des fournisseurs actifs (tenant courant), ordonnes par nom. */
    List<Fournisseur> findByActifTrueOrderByNomFournisseurAsc();

    /** Page des fournisseurs (filtree LIKE sur nom_fournisseur insensible casse). */
    @Query("SELECT f FROM Fournisseur f WHERE "
            + "(:recherche IS NULL OR :recherche = '' OR "
            + " LOWER(f.nomFournisseur) LIKE LOWER(CONCAT('%', :recherche, '%')))")
    Page<Fournisseur> search(@Param("recherche") String recherche, Pageable pageable);
}

package com.cityprojects.citybackend.repository.hebergement;

import com.cityprojects.citybackend.entity.hebergement.TypeChambre;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository des types de chambres.
 *
 * <p>Hibernate ajoute automatiquement {@code WHERE hotel_id = ?} sur toutes les
 * requetes via {@link org.hibernate.annotations.TenantId}. Les methodes
 * N'ACCEPTENT donc PAS de parametre {@code hotelId}.</p>
 */
@Repository
public interface TypeChambreRepository
        extends JpaRepository<TypeChambre, Long>, JpaSpecificationExecutor<TypeChambre> {

    /** Liste les types actifs ordonnes par nom (tenant courant). */
    List<TypeChambre> findByActifTrueOrderByTypeNomAsc();

    /** Pagination par flag actif (tenant courant). */
    Page<TypeChambre> findByActifOrderByTypeNomAsc(Boolean actif, Pageable pageable);

    /**
     * Pagination par flag actif (tenant courant) sans tri implicite : permet
     * d'appliquer un tri stable depuis le service via {@link Pageable#getSort()}
     * (Tour 14 audit, finding I2).
     */
    Page<TypeChambre> findByActif(Boolean actif, Pageable pageable);

    /** Pagination tous statuts (tenant courant). */
    Page<TypeChambre> findAllByOrderByTypeNomAsc(Pageable pageable);

    /** Test d'unicite du code (tenant courant). */
    boolean existsByTypeCode(String typeCode);
}

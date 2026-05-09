package com.cityprojects.citybackend.repository.client;

import com.cityprojects.citybackend.entity.client.Societe;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository des societes.
 * <p>
 * Le filtre {@code WHERE hotel_id = ?} est ajoute automatiquement par Hibernate
 * sur toutes les requetes (SELECT/UPDATE/DELETE) grace a {@link org.hibernate.annotations.TenantId}
 * sur {@link Societe#getHotelId()}. Les methodes ci-dessous N'ACCEPTENT donc PAS
 * de parametre {@code hotelId} : passer le tenant via le {@link com.cityprojects.citybackend.common.tenant.TenantContext}
 * et laisser Hibernate appliquer le filtre.
 */
@Repository
public interface SocieteRepository
        extends JpaRepository<Societe, Long>, JpaSpecificationExecutor<Societe> {

    /**
     * Page de societes actives, ordonnees par nom.
     * <p>
     * Pas de variante {@code List<Societe>} : ecartee au Tour 9bis (risque OOM).
     * {@link com.cityprojects.citybackend.service.client.SocieteService#findAllActive()}
     * appelle cette variante avec un plafond defensif et journalise un WARN
     * si la limite est atteinte.
     */
    Page<Societe> findByActifTrueOrderBySocieteNomAsc(Pageable pageable);

    /**
     * Recherche par nom (LIKE insensible a la casse) parmi les societes actives.
     * Utilise dans le service pour la recherche libre.
     */
    @Query("SELECT s FROM Societe s WHERE s.actif = true "
            + "AND LOWER(s.societeNom) LIKE LOWER(CONCAT('%', :recherche, '%'))")
    Page<Societe> searchSocietes(@Param("recherche") String recherche, Pageable pageable);

    /**
     * Test d'unicite du nom (ignore la casse) au sein du tenant courant.
     * Utilise pour la validation a la creation.
     */
    boolean existsBySocieteNomIgnoreCase(String societeNom);

    /**
     * Test d'unicite du SIRET au sein du tenant courant.
     */
    boolean existsBySiret(String siret);

    /**
     * Retourne la societe ayant ce nom exact (ignore la casse) au sein du tenant courant.
     * Utilise par la validation a la modification (exclusion de la societe en cours).
     */
    Optional<Societe> findBySocieteNomIgnoreCase(String societeNom);

    /**
     * Retourne la societe ayant ce SIRET au sein du tenant courant.
     */
    Optional<Societe> findBySiret(String siret);

    /**
     * Compte des societes actives dans le tenant courant.
     */
    long countByActifTrue();
}

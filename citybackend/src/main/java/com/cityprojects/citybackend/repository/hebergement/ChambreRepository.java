package com.cityprojects.citybackend.repository.hebergement;

import com.cityprojects.citybackend.dto.reporting.projection.TypeChambreCountProjection;
import com.cityprojects.citybackend.entity.hebergement.Chambre;
import com.cityprojects.citybackend.entity.hebergement.StatutChambre;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repository des chambres physiques.
 *
 * <p>Hibernate ajoute automatiquement {@code WHERE hotel_id = ?} via
 * {@link org.hibernate.annotations.TenantId}.</p>
 */
@Repository
public interface ChambreRepository
        extends JpaRepository<Chambre, Long>, JpaSpecificationExecutor<Chambre> {

    /** Liste des chambres actives, ordonnees par numero (tenant courant). */
    List<Chambre> findByActifTrueOrderByNumeroChambreAsc();

    /** Pagination tous statuts (tenant courant). */
    Page<Chambre> findAllByOrderByNumeroChambreAsc(Pageable pageable);

    /** Recherche par numero unique au sein du tenant courant. */
    Optional<Chambre> findByNumeroChambre(String numeroChambre);

    /** Test d'unicite du numero (tenant courant). */
    boolean existsByNumeroChambre(String numeroChambre);

    /** Liste des chambres actives d'un type donne. */
    List<Chambre> findByTypeIdAndActifTrueOrderByNumeroChambreAsc(Long typeId);

    /** Liste des chambres actives ayant un statut donne. */
    List<Chambre> findByStatutAndActifTrueOrderByNumeroChambreAsc(StatutChambre statut);

    /**
     * Liste les chambres actives dont {@code nb_personnes_max &gt;= :minPersonnes}
     * (filtre capacite optionnel). Tour 14 B2 API - aide pour
     * {@code rechercher-disponibilite}.
     */
    List<Chambre> findByActifTrueAndNbPersonnesMaxGreaterThanEqualOrderByNumeroChambreAsc(Integer minPersonnes);

    /**
     * Chambres disponibles sur la periode {@code [:dateDebut, :dateFin)} :
     * actives et sans aucun pivot {@code reservations_chambres} chevauchant
     * la periode (Tour 14 B2 API).
     *
     * <p>Filtre tenant : Hibernate ajoute {@code AND c.hotelId = ?} via
     * {@code @TenantId} ; la sous-requete sur {@code ReservationChambre} est
     * filtree de la meme facon.</p>
     */
    @Query("SELECT c FROM Chambre c WHERE c.actif = true "
            + "AND c.chambreId NOT IN ("
            + "  SELECT rc.chambreId FROM ReservationChambre rc "
            + "  WHERE rc.dateDebut < :dateFin AND rc.dateFin > :dateDebut"
            + ") "
            + "ORDER BY c.numeroChambre ASC")
    List<Chambre> findDisponibles(@Param("dateDebut") LocalDate dateDebut,
                                  @Param("dateFin") LocalDate dateFin);

    /**
     * Variante de {@link #findDisponibles(LocalDate, LocalDate)} avec filtre
     * capacite minimale.
     */
    @Query("SELECT c FROM Chambre c WHERE c.actif = true "
            + "AND c.nbPersonnesMax >= :minPersonnes "
            + "AND c.chambreId NOT IN ("
            + "  SELECT rc.chambreId FROM ReservationChambre rc "
            + "  WHERE rc.dateDebut < :dateFin AND rc.dateFin > :dateDebut"
            + ") "
            + "ORDER BY c.numeroChambre ASC")
    List<Chambre> findDisponiblesAvecCapacite(@Param("dateDebut") LocalDate dateDebut,
                                              @Param("dateFin") LocalDate dateFin,
                                              @Param("minPersonnes") Integer minPersonnes);

    // ============================================================================
    // Tour 40 — Reporting MVP : agregats pour R-HEB-001 (occupation).
    // Filtre tenant ajoute automatiquement par Hibernate via @TenantId.
    // ============================================================================

    /** Compte les chambres actives du tenant courant (R-HEB-001 total). */
    long countByActifTrue();

    /**
     * Compte les chambres actives par type (R-HEB-001 breakdown).
     *
     * <p>Jointure avec {@code TypeChambre} pour exposer le code et le nom du type
     * directement dans la projection.</p>
     */
    @Query("SELECT t.typeId AS typeId, t.typeCode AS typeCode, t.typeNom AS typeNom, "
            + " COUNT(c) AS nbChambres "
            + "FROM Chambre c, com.cityprojects.citybackend.entity.hebergement.TypeChambre t "
            + "WHERE c.typeId = t.typeId AND c.actif = true "
            + "GROUP BY t.typeId, t.typeCode, t.typeNom "
            + "ORDER BY t.typeCode ASC")
    List<TypeChambreCountProjection> countActivesGroupedByType();
}

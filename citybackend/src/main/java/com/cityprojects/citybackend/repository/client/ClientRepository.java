package com.cityprojects.citybackend.repository.client;

import com.cityprojects.citybackend.entity.client.Client;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

/**
 * Repository des clients (personnes physiques).
 * <p>
 * Le filtre {@code WHERE hotel_id = ?} est ajoute automatiquement par Hibernate
 * sur toutes les requetes grace a {@link org.hibernate.annotations.TenantId}
 * sur {@link Client#getHotelId()}. Les methodes N'ACCEPTENT donc PAS de
 * parametre {@code hotelId} : utiliser le {@link com.cityprojects.citybackend.common.tenant.TenantContext}.
 */
@Repository
public interface ClientRepository
        extends JpaRepository<Client, Long>, JpaSpecificationExecutor<Client> {

    /**
     * Page des clients actifs, ordonnes par nom puis prenom.
     * <p>
     * Pas de variante {@code List<Client>} : ecartee au Tour 9bis (dead code,
     * risque OOM, aucun appelant). Toujours utiliser cette variante paginee.
     */
    Page<Client> findByActifTrueOrderByNomAscPrenomAsc(Pageable pageable);

    /**
     * Page des clients d'une societe donnee (tous statuts), ordonnes par nom/prenom.
     */
    Page<Client> findBySocieteIdOrderByNomAscPrenomAsc(Long societeId, Pageable pageable);

    /**
     * Page des clients actifs sans societe (B2C), ordonnes par nom/prenom.
     */
    Page<Client> findBySocieteIdIsNullAndActifTrueOrderByNomAscPrenomAsc(Pageable pageable);

    /**
     * Recherche libre sur nom, prenom, email, numero_client (LIKE insensible casse)
     * parmi les clients actifs du tenant courant.
     */
    @Query("SELECT c FROM Client c WHERE c.actif = true AND ("
            + "LOWER(c.nom) LIKE LOWER(CONCAT('%', :recherche, '%')) OR "
            + "LOWER(c.prenom) LIKE LOWER(CONCAT('%', :recherche, '%')) OR "
            + "LOWER(c.email) LIKE LOWER(CONCAT('%', :recherche, '%')) OR "
            + "LOWER(c.numeroClient) LIKE LOWER(CONCAT('%', :recherche, '%')))")
    Page<Client> searchClients(@Param("recherche") String recherche, Pageable pageable);

    /**
     * Test d'unicite de l'email (ignore la casse) au sein du tenant courant.
     */
    boolean existsByEmailIgnoreCase(String email);

    /**
     * Test d'unicite du numero_client au sein du tenant courant.
     * Combine au {@code UNIQUE (hotel_id, numero_client)} cote SQL pour
     * un double rempart : l'application verifie en amont (message i18n
     * propre) et la base garantit la coherence en cas de course condition.
     */
    boolean existsByNumeroClient(String numeroClient);

    /**
     * Recherche un client par email (ignore casse) dans le tenant courant.
     */
    Optional<Client> findByEmailIgnoreCase(String email);

    /**
     * Recherche un client par numero_client dans le tenant courant.
     */
    Optional<Client> findByNumeroClient(String numeroClient);

    /**
     * Compte des clients actifs rattaches a une societe donnee (tenant courant).
     * Utilise pour bloquer la suppression d'une societe ayant des clients actifs.
     */
    long countBySocieteIdAndActifTrue(Long societeId);

    /**
     * Compte des clients créés dans une fenêtre temporelle (tenant courant).
     * Utilisé par le KPI "Clients Nouveaux" du dashboard accueil.
     */
    long countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(Instant from, Instant to);
}

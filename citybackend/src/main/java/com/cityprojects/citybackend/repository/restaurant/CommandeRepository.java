package com.cityprojects.citybackend.repository.restaurant;

import com.cityprojects.citybackend.entity.restaurant.Commande;
import com.cityprojects.citybackend.entity.restaurant.ModeReglementCommande;
import com.cityprojects.citybackend.entity.restaurant.StatutCommande;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository des commandes POS restaurant (Tour 24).
 *
 * <p>Hibernate ajoute automatiquement {@code WHERE hotel_id = ?} via
 * {@link org.hibernate.annotations.TenantId}. Les methodes N'ACCEPTENT donc
 * PAS de parametre {@code hotelId}.</p>
 */
@Repository
public interface CommandeRepository
        extends JpaRepository<Commande, Long>, JpaSpecificationExecutor<Commande> {

    /** Recherche par numero metier (tenant courant). */
    Optional<Commande> findByNumeroCommande(String numeroCommande);

    /** Test d'unicite du numero (tenant courant). */
    boolean existsByNumeroCommande(String numeroCommande);

    /** Page des commandes filtree par statut, ordonnee par date de commande descendante. */
    Page<Commande> findByStatutOrderByDateCommandeDesc(StatutCommande statut, Pageable pageable);

    /**
     * Commandes liees a une reservation hebergement (mode REPORTE_CHAMBRE).
     * Utilise au check-out pour facturer les commandes reportees.
     */
    List<Commande> findByReservationIdOrderByDateCommandeAsc(Long reservationId);

    /** Page des commandes d'un client. */
    Page<Commande> findByClientIdOrderByDateCommandeDesc(Long clientId, Pageable pageable);

    /**
     * Commandes liees a une reservation, mode reglement specifique, et NON
     * encore facturees (factureId IS NULL). Utilise au check-out pour facturer
     * les commandes REPORTE_CHAMBRE en bloc avec la facture sejour (Tour 25).
     */
    List<Commande> findByReservationIdAndModeReglementAndFactureIdIsNull(
            Long reservationId, ModeReglementCommande modeReglement);

    /**
     * Compte les commandes dont {@code dateCommande} appartient a la fenetre
     * {@code [start, end)} (UTC) - utilise par la cloture de caisse Tour 26.1
     * pour compter les commandes encaissees / annulees du jour. Hibernate
     * applique le filtre tenant.
     */
    long countByDateCommandeBetweenAndStatut(Instant startInclusive, Instant endExclusive,
                                             StatutCommande statut);

    /**
     * Compte les commandes dont {@code dateCommande} appartient a la fenetre
     * {@code [start, end)} (UTC) ET dont {@code factureId} est non null
     * (= commandes encaissees comptant le jour J). Hibernate applique le
     * filtre tenant.
     */
    long countByDateCommandeBetweenAndFactureIdIsNotNull(Instant startInclusive,
                                                         Instant endExclusive);
}

package com.cityprojects.citybackend.service.hebergement;

import com.cityprojects.citybackend.dto.hebergement.ChambreDto;
import com.cityprojects.citybackend.dto.hebergement.NuiteeDto;
import com.cityprojects.citybackend.dto.hebergement.RechercheDisponibiliteRequest;
import com.cityprojects.citybackend.dto.hebergement.ReservationCreateDto;
import com.cityprojects.citybackend.dto.hebergement.ReservationDto;
import com.cityprojects.citybackend.entity.hebergement.StatutReservation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;

/**
 * Service de gestion des reservations.
 *
 * <p>Toutes les methodes operent dans le tenant courant.
 * Le {@code numeroReservation} est genere via
 * {@link com.cityprojects.citybackend.service.finance.NumerotationService}
 * (type {@link com.cityprojects.citybackend.service.finance.TypeNumerotation#RES}).
 * Le {@code userId} createur est extrait du {@code SecurityContext}.</p>
 */
public interface ReservationService {

    /**
     * Cree une nouvelle reservation. Genere automatiquement
     * {@code numeroReservation} (RES-...) et les nuitees correspondantes.
     *
     * @throws com.cityprojects.citybackend.exception.BusinessException
     *         si dates invalides, conflit de chambre, etc.
     * @throws com.cityprojects.citybackend.exception.ResourceNotFoundException
     *         si client ou societe introuvable.
     */
    ReservationDto create(ReservationCreateDto dto);

    /**
     * Met a jour les champs editables d'une reservation existante (Tour 14 B2 API).
     *
     * <p>Periode {@code (dateArrivee, dateDepart)}, {@code nbAdultes},
     * {@code nbEnfants}, {@code motifSejour}, {@code commentaires},
     * {@code reductionPourcentage}. Refuse si la reservation est terminee
     * ({@code PARTIE}, {@code ANNULEE}, {@code NO_SHOW}). Les modifications
     * de chambres / clients additionnels ne sont PAS prises en charge ici
     * (workflow dedie a venir).</p>
     */
    ReservationDto update(Long reservationId, ReservationCreateDto dto);

    /**
     * Recupere une reservation par son id (filtre tenant via Hibernate).
     */
    ReservationDto findById(Long reservationId);

    /**
     * Recupere une reservation par son numero metier
     * (RES-{exercice}-{codePays}-{6 chiffres}).
     */
    ReservationDto findByNumero(String numeroReservation);

    /**
     * Page des reservations, eventuellement filtree par statut. Tri stable
     * garanti (Tour 14 audit, finding I2).
     */
    Page<ReservationDto> findAll(StatutReservation statut, Pageable pageable);

    /**
     * Page des reservations d'un client donne.
     */
    Page<ReservationDto> findByClient(Long clientId, Pageable pageable);

    /**
     * Liste les nuitees d'une reservation, par date croissante.
     */
    List<NuiteeDto> findNuitees(Long reservationId);

    /**
     * Liste les reservations dont la dateArrivee = aujourd'hui (Africa/Nouakchott)
     * et dont le statut est encore CONFIRMEE (check-in du jour, non encore fait).
     */
    List<ReservationDto> findArriveesToday();

    /**
     * Liste les reservations dont la dateDepart = aujourd'hui (Africa/Nouakchott)
     * et dont le statut est ARRIVEE (check-out du jour, non encore fait).
     */
    List<ReservationDto> findDepartsToday();

    /**
     * Liste les reservations en cours : check-in fait (statut ARRIVEE), check-out
     * non encore fait.
     */
    List<ReservationDto> findEnCours();

    /**
     * Liste les check-ins en retard : statut CONFIRMEE et dateArrivee strictement
     * anterieure a aujourd'hui (no-show pas encore traite par le night audit).
     */
    List<ReservationDto> findCheckInsRetard();

    /**
     * Recherche libre dans les reservations du tenant courant : LIKE insensible
     * casse sur {@code numero_reservation}, nom et prenom du client principal,
     * et son telephone.
     */
    Page<ReservationDto> rechercher(String terme, Pageable pageable);

    /**
     * Recherche les chambres disponibles pour une periode et un nombre de
     * personnes optionnel (filtrage capacite).
     */
    List<ChambreDto> rechercherDisponibilite(RechercheDisponibiliteRequest request);

    /**
     * Effectue le check-in : statut CONFIRMEE -&gt; ARRIVEE, chambres OCCUPEEs.
     */
    ReservationDto checkIn(Long reservationId);

    /**
     * Effectue le check-out : statut ARRIVEE -&gt; PARTIE, chambres NETTOYAGE.
     */
    ReservationDto checkOut(Long reservationId);

    /**
     * Annule une reservation (motif obligatoire). Si etait ARRIVEE, libere
     * les chambres.
     */
    ReservationDto cancel(Long reservationId, String motif);

    /**
     * Supprime (logiquement : cancel) une reservation (Tour 14 B2 API).
     * <p>Le DELETE HTTP est traduit en cancel sans motif metier
     * specifique : on conserve la reservation en base avec le statut
     * {@code ANNULEE} pour la tracabilite. Pas de DELETE physique.</p>
     */
    ReservationDto delete(Long reservationId);
}

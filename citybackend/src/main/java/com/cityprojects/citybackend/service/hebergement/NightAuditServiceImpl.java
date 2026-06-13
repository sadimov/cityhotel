package com.cityprojects.citybackend.service.hebergement;

import com.cityprojects.citybackend.common.tenant.RequireTenant;
import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.dto.finance.RecapPaiementsReservationDto;
import com.cityprojects.citybackend.dto.hebergement.CheckOutExpressRequest;
import com.cityprojects.citybackend.dto.hebergement.NightAuditResultDto;
import com.cityprojects.citybackend.entity.hebergement.JourneeHoteliere;
import com.cityprojects.citybackend.entity.hebergement.Nuitee;
import com.cityprojects.citybackend.entity.hebergement.Reservation;
import com.cityprojects.citybackend.entity.hebergement.ReservationChambre;
import com.cityprojects.citybackend.entity.hebergement.StatutNuitee;
import com.cityprojects.citybackend.entity.hebergement.StatutReservation;
import com.cityprojects.citybackend.repository.hebergement.NuiteeRepository;
import com.cityprojects.citybackend.repository.hebergement.ReservationChambreRepository;
import com.cityprojects.citybackend.repository.hebergement.ReservationRepository;
import com.cityprojects.citybackend.security.UserPrincipal;
import com.cityprojects.citybackend.service.finance.ReservationFinanceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Implementation du night audit.
 *
 * <p>Opère sur la <b>date hôtelière</b> matérialisée (cf. {@link HotelDayService})
 * au lieu de {@code LocalDate.now()}.</p>
 *
 * <h3>Cycle d'exécution</h3>
 * <ol>
 *   <li>{@link HotelDayService#startClosure(Long)} : OUVERTE → CLOTURE_EN_COURS.
 *       Le {@code NightAuditLockRegistry} marque le tenant comme verrouillé.
 *       Tout write HTTP est rejeté en 423 par {@code NightAuditLockFilter}.</li>
 *   <li><b>Auto check-out des départs du jour</b> (dateDepart = dateHotel, statut
 *       ARRIVEE) :
 *     <ul>
 *       <li>résa avec {@code societeId} + reste à payer &gt; 0 → {@code checkOutExpress()}
 *           (transfert client → société) ;</li>
 *       <li>autres cas → {@code checkOut()} standard. Pour une B2C impayée, la dette
 *           résiduelle reste sur le compte client auxiliaire (doctrine Tour 20).</li>
 *     </ul>
 *   </li>
 *   <li><b>NO_SHOW étendu</b> : marque les réservations CONFIRMEE dont
 *       {@code dateArrivee &lt;= dateHotel} (incluant les arrivées du jour qui
 *       n'ont pas fait leur check-in).</li>
 *   <li><b>Nuitées manquantes</b> pour les séjours ARRIVEE en cours, sur la
 *       période [dateDebut, min(dateHotel, dateFin)).</li>
 *   <li>{@link HotelDayService#completeClosure()} : CLOTURE_EN_COURS → CLOTUREE
 *       + ouverture de J+1. Le verrou HTTP est levé.</li>
 *   <li>Exception : {@link HotelDayService#abortClosure()} rollback OUVERTE.</li>
 * </ol>
 *
 * <h3>Résilience par-réservation</h3>
 * <p>Les check-out auto et les NO_SHOW sont encapsulés individuellement :
 * une exception sur une résa loggue + incrémente {@code nbErreurs} mais
 * n'interrompt pas le run global. Les chambres déjà traitées restent à jour ;
 * l'opérateur traitera les résa en erreur manuellement le matin.</p>
 *
 * <p>Idempotence métier conservée : filtres sur statut + marqueurs
 * {@code existsByReservationIdAndChambreIdAndDateNuit}.</p>
 */
@Service
@RequireTenant
@Transactional(readOnly = true)
public class NightAuditServiceImpl implements NightAuditService {

    private static final Logger logger = LoggerFactory.getLogger(NightAuditServiceImpl.class);

    private final ReservationRepository reservationRepository;
    private final ReservationChambreRepository reservationChambreRepository;
    private final NuiteeRepository nuiteeRepository;
    private final HotelDayService hotelDayService;
    private final ReservationService reservationService;
    private final ReservationFinanceService reservationFinanceService;
    private final Clock clock;

    public NightAuditServiceImpl(ReservationRepository reservationRepository,
                                 ReservationChambreRepository reservationChambreRepository,
                                 NuiteeRepository nuiteeRepository,
                                 HotelDayService hotelDayService,
                                 @Lazy ReservationService reservationService,
                                 ReservationFinanceService reservationFinanceService,
                                 Clock clock) {
        this.reservationRepository = reservationRepository;
        this.reservationChambreRepository = reservationChambreRepository;
        this.nuiteeRepository = nuiteeRepository;
        this.hotelDayService = hotelDayService;
        // @Lazy : ReservationService et NightAuditService sont enregistres dans
        // le meme module hebergement ; @Lazy casse un cycle potentiel via les
        // listeners d'events (cf. ReservationCheckedOutEvent + MenagePlanning).
        this.reservationService = reservationService;
        this.reservationFinanceService = reservationFinanceService;
        this.clock = clock;
    }

    @Override
    @Transactional
    public NightAuditResultDto run() {
        Long hotelId = TenantContext.get();
        Long userId = currentUserIdOrNull();

        JourneeHoteliere day = hotelDayService.startClosure(userId);
        LocalDate dateHotel = day.getDateHotel();
        logger.info("Night audit démarré : hotelId={}, dateHotel={}, userId={}",
                hotelId, dateHotel, userId);

        Counters counters;
        try {
            counters = new Counters();
            processDeparturesOfDay(dateHotel, counters);
            counters.nbNoShow = markNoShowReservations(dateHotel, counters);
            counters.nbNuiteesManquantes = generateMissingNuitees(dateHotel);
        } catch (RuntimeException ex) {
            logger.error("Night audit échoué pour hotelId={}, dateHotel={} — abort closure",
                    hotelId, dateHotel, ex);
            hotelDayService.abortClosure();
            throw ex;
        }

        hotelDayService.completeClosure();
        Instant executedAt = Instant.now(clock);
        logger.info("Night audit terminé : hotelId={}, dateHotel={}, nbNoShow={}, "
                        + "nbNuiteesGenerees={}, nbCheckOutAuto={}, nbCheckOutExpressAuto={}, nbErreurs={}",
                hotelId, dateHotel, counters.nbNoShow, counters.nbNuiteesManquantes,
                counters.nbCheckOutAuto, counters.nbCheckOutExpressAuto, counters.nbErreurs);

        return new NightAuditResultDto(
                hotelId, dateHotel,
                counters.nbNoShow, counters.nbNuiteesManquantes,
                counters.nbCheckOutAuto, counters.nbCheckOutExpressAuto,
                counters.nbErreurs, executedAt);
    }

    /**
     * Pour chaque réservation ARRIVEE dont {@code dateDepart = dateHotel} :
     * <ul>
     *   <li>si la résa a une {@code societeId} ET la facture liée a un reste à
     *       payer &gt; 0 → {@code checkOutExpress()} (transfert B2B vers société) ;</li>
     *   <li>sinon → {@code checkOut()} standard (paiement complet OU dette
     *       résiduelle laissée sur le compte client auxiliaire).</li>
     * </ul>
     *
     * <p>Les exceptions sur une résa sont loggées + comptées dans
     * {@code counters.nbErreurs} sans interrompre la boucle.</p>
     */
    private void processDeparturesOfDay(LocalDate dateHotel, Counters counters) {
        List<Reservation> departs = reservationRepository
                .findByDateDepartAndStatutOrderByDateDepartAsc(dateHotel, StatutReservation.ARRIVEE);
        for (Reservation reservation : departs) {
            Long reservationId = reservation.getReservationId();
            try {
                BigDecimal reste = safeResteAPayer(reservationId);
                boolean impayee = reste != null && reste.compareTo(BigDecimal.ZERO) > 0;
                Long societeId = reservation.getSocieteId();

                if (impayee && societeId != null) {
                    // Cas B : B2B impayée → transfert client → société.
                    reservationService.checkOutExpress(reservationId,
                            new CheckOutExpressRequest(societeId, reservation.getClientPrincipalId()));
                    counters.nbCheckOutExpressAuto++;
                    logger.info("Auto check-out express : reservation={}, societe={}, reste={}",
                            reservationId, societeId, reste);
                } else {
                    // Cas A (payée) ou C (B2C impayée — dette reste sur le client).
                    reservationService.checkOut(reservationId);
                    counters.nbCheckOutAuto++;
                    if (impayee) {
                        logger.warn("Auto check-out forcé (B2C impayée) : reservation={}, reste={} "
                                + "→ dette laissée sur compte client auxiliaire",
                                reservationId, reste);
                    } else {
                        logger.info("Auto check-out standard : reservation={}", reservationId);
                    }
                }
            } catch (RuntimeException ex) {
                counters.nbErreurs++;
                logger.error("Erreur auto check-out reservation={} : {}", reservationId, ex.getMessage(), ex);
            }
        }
    }

    /**
     * Calcule le reste à payer de la réservation. Renvoie {@link BigDecimal#ZERO}
     * si aucune facture n'est trouvée (résa pas encore facturée — pas de dette
     * connue côté finance).
     */
    private BigDecimal safeResteAPayer(Long reservationId) {
        try {
            RecapPaiementsReservationDto recap = reservationFinanceService
                    .getRecapForReservation(reservationId);
            return recap.resteGlobal() != null ? recap.resteGlobal() : BigDecimal.ZERO;
        } catch (RuntimeException ex) {
            logger.warn("Reste à payer indéterminé pour reservation={} : {} — traité comme payée",
                    reservationId, ex.getMessage());
            return BigDecimal.ZERO;
        }
    }

    /**
     * Marque comme NO_SHOW les réservations CONFIRMEE dont {@code dateArrivee &lt;= dateHotel}.
     * Inclut les arrivées du jour qui n'ont pas fait leur check-in (consigne user 2026-06-12 §2).
     *
     * <p>Les transitions individuelles qui échouent sont comptées dans
     * {@code counters.nbErreurs} mais ne bloquent pas le run.</p>
     */
    private int markNoShowReservations(LocalDate dateHotel, Counters counters) {
        List<Reservation> candidates = reservationRepository
                .findByStatutAndDateArriveeLessThanEqual(StatutReservation.CONFIRMEE, dateHotel);
        int count = 0;
        for (Reservation r : candidates) {
            try {
                r.setStatut(StatutReservation.NO_SHOW);
                reservationRepository.save(r);
                count++;
                logger.info("NO_SHOW : reservation id={}, numero={}, dateArrivee={}",
                        r.getReservationId(), r.getNumeroReservation(), r.getDateArrivee());
            } catch (RuntimeException ex) {
                counters.nbErreurs++;
                logger.error("Erreur NO_SHOW reservation={} : {}",
                        r.getReservationId(), ex.getMessage(), ex);
            }
        }
        return count;
    }

    /**
     * Pour les séjours ARRIVEE, génère les nuitées manquantes sur
     * [dateDebut, min(dateHotel, dateFin)).
     */
    private int generateMissingNuitees(LocalDate dateHotel) {
        List<Reservation> active = reservationRepository.findByStatut(StatutReservation.ARRIVEE);
        int count = 0;
        for (Reservation reservation : active) {
            List<ReservationChambre> pivots = reservationChambreRepository
                    .findByReservationIdOrderByDateDebutAsc(reservation.getReservationId());
            for (ReservationChambre pivot : pivots) {
                LocalDate fin = pivot.getDateFin().isBefore(dateHotel) ? pivot.getDateFin() : dateHotel;
                LocalDate jour = pivot.getDateDebut();
                while (jour.isBefore(fin)) {
                    if (!nuiteeRepository.existsByReservationIdAndChambreIdAndDateNuit(
                            reservation.getReservationId(), pivot.getChambreId(), jour)) {
                        Nuitee nuitee = new Nuitee();
                        nuitee.setReservationId(reservation.getReservationId());
                        nuitee.setChambreId(pivot.getChambreId());
                        nuitee.setDateNuit(jour);
                        nuitee.setPrixNuit(pivot.getPrixNuit());
                        nuitee.setTaxeSejour(BigDecimal.ZERO);
                        nuitee.setStatut(StatutNuitee.CONSOMMEE);
                        nuiteeRepository.save(nuitee);
                        count++;
                        logger.info("Nuitee manquante generee : reservationId={}, chambreId={}, dateNuit={}",
                                reservation.getReservationId(), pivot.getChambreId(), jour);
                    }
                    jour = jour.plusDays(1);
                }
            }
        }
        return count;
    }

    /**
     * Récupère l'userId du principal courant, ou {@code null} en mode
     * scheduler / batch sans SecurityContext.
     */
    private Long currentUserIdOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            return principal.getUserId();
        }
        return null;
    }

    /** Compteurs internes pour le résumé final. */
    private static final class Counters {
        int nbNoShow;
        int nbNuiteesManquantes;
        int nbCheckOutAuto;
        int nbCheckOutExpressAuto;
        int nbErreurs;
    }
}

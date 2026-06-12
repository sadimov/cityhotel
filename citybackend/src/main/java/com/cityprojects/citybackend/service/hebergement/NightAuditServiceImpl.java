package com.cityprojects.citybackend.service.hebergement;

import com.cityprojects.citybackend.common.tenant.RequireTenant;
import com.cityprojects.citybackend.common.tenant.TenantContext;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * <p>Refonte : opère désormais sur la <b>date hôtelière</b> matérialisée
 * (cf. {@link HotelDayService}) au lieu de {@code LocalDate.now()}.
 * Cf. {@code règles_night_audit.txt} §1 — le système reste « dans » la
 * journée hôtelière tant que le NA n'a pas tourné.</p>
 *
 * <h3>Cycle d'exécution</h3>
 * <ol>
 *   <li>{@link HotelDayService#startClosure(Long)} : OUVERTE → CLOTURE_EN_COURS.
 *       Le {@code NightAuditLockRegistry} marque le tenant comme verrouillé.
 *       À partir de cet instant et jusqu'à completeClosure/abortClosure, tout
 *       write HTTP est rejeté en 423 par {@code NightAuditLockFilter}.</li>
 *   <li>Marque les réservations CONFIRMEE en retard comme NO_SHOW.</li>
 *   <li>Génère les nuitées manquantes pour les séjours ARRIVEE en cours, sur la
 *       période [dateDebut, min(dateHotel, dateFin)).</li>
 *   <li>{@link HotelDayService#completeClosure()} : CLOTURE_EN_COURS → CLOTUREE,
 *       et ouverture immédiate de la journée J+1.</li>
 *   <li>En cas d'exception : {@link HotelDayService#abortClosure()} rollback
 *       l'état CLOTURE_EN_COURS vers OUVERTE.</li>
 * </ol>
 *
 * <p>Idempotence métier conservée : les filtres {@code statut=CONFIRMEE} et
 * {@code existsByReservationIdAndChambreIdAndDateNuit} évitent les doublons.
 * La transition de journée elle-même n'est PAS idempotente — un second appel
 * sur une journée déjà CLOTURE_EN_COURS retourne 400
 * ({@code error.nightAudit.alreadyRunning}).</p>
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
    private final Clock clock;

    public NightAuditServiceImpl(ReservationRepository reservationRepository,
                                 ReservationChambreRepository reservationChambreRepository,
                                 NuiteeRepository nuiteeRepository,
                                 HotelDayService hotelDayService,
                                 Clock clock) {
        this.reservationRepository = reservationRepository;
        this.reservationChambreRepository = reservationChambreRepository;
        this.nuiteeRepository = nuiteeRepository;
        this.hotelDayService = hotelDayService;
        this.clock = clock;
    }

    @Override
    @Transactional
    public NightAuditResultDto run() {
        Long hotelId = TenantContext.get();
        Long userId = currentUserIdOrNull();

        // 1) Verrou logique de la journée — toute écriture HTTP concurrente
        //    sera rejetée en 423 par NightAuditLockFilter à partir d'ici.
        JourneeHoteliere day = hotelDayService.startClosure(userId);
        LocalDate dateHotel = day.getDateHotel();
        logger.info("Night audit démarré : hotelId={}, dateHotel={}, userId={}",
                hotelId, dateHotel, userId);

        int nbNoShow;
        int nbNuiteesManquantes;
        try {
            // 2) Effets métier (sur la date hôtelière, pas LocalDate.now()).
            nbNoShow = markNoShowReservations(dateHotel);
            nbNuiteesManquantes = generateMissingNuitees(dateHotel);
        } catch (RuntimeException ex) {
            // 3) Rollback de la journée si quoi que ce soit casse pendant
            //    l'exécution métier. La transaction Spring rollback aussi les
            //    INSERT/UPDATE faits jusque-là (atomicité).
            logger.error("Night audit échoué pour hotelId={}, dateHotel={} — abort closure",
                    hotelId, dateHotel, ex);
            hotelDayService.abortClosure();
            throw ex;
        }

        // 4) Fermeture définitive + ouverture de la journée J+1.
        hotelDayService.completeClosure();
        Instant executedAt = Instant.now(clock);
        logger.info("Night audit terminé : hotelId={}, dateHotel={}, nbNoShow={}, nbNuiteesGenerees={}",
                hotelId, dateHotel, nbNoShow, nbNuiteesManquantes);

        return new NightAuditResultDto(hotelId, dateHotel, nbNoShow, nbNuiteesManquantes, executedAt);
    }

    /**
     * Marque les réservations CONFIRMEE dont la date d'arrivée est dépassée
     * comme NO_SHOW (référence : date hôtelière, pas le jour calendaire).
     */
    private int markNoShowReservations(LocalDate dateHotel) {
        List<Reservation> candidates = reservationRepository
                .findByStatutAndDateArriveeBefore(StatutReservation.CONFIRMEE, dateHotel);
        int count = 0;
        for (Reservation r : candidates) {
            r.setStatut(StatutReservation.NO_SHOW);
            reservationRepository.save(r);
            count++;
            logger.info("NO_SHOW : reservation id={}, numero={}, dateArrivee={}",
                    r.getReservationId(), r.getNumeroReservation(), r.getDateArrivee());
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
}

package com.cityprojects.citybackend.service.hebergement;

import com.cityprojects.citybackend.common.tenant.RequireTenant;
import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.dto.hebergement.NightAuditResultDto;
import com.cityprojects.citybackend.entity.hebergement.Nuitee;
import com.cityprojects.citybackend.entity.hebergement.Reservation;
import com.cityprojects.citybackend.entity.hebergement.ReservationChambre;
import com.cityprojects.citybackend.entity.hebergement.StatutNuitee;
import com.cityprojects.citybackend.entity.hebergement.StatutReservation;
import com.cityprojects.citybackend.repository.hebergement.NuiteeRepository;
import com.cityprojects.citybackend.repository.hebergement.ReservationChambreRepository;
import com.cityprojects.citybackend.repository.hebergement.ReservationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Implementation du night audit (Tour 13).
 *
 * <p>Conventions appliquees (cf. citybackend/CLAUDE.md §3.3) :
 * <ul>
 *   <li>{@code @RequireTenant} au niveau classe : refuse l'execution sans
 *       {@link TenantContext} positionne (le scheduler est responsable de
 *       positionner / nettoyer le tenant pour chaque hotel actif).</li>
 *   <li>{@code @Transactional(readOnly = true)} a la classe, override en ecriture
 *       sur {@link #run()}.</li>
 *   <li>Constructeur explicite (palier 1, sans Lombok).</li>
 *   <li>{@link Clock} injecte pour testabilite (peut etre fige a une date precise).</li>
 * </ul>
 */
@Service
@RequireTenant
@Transactional(readOnly = true)
public class NightAuditServiceImpl implements NightAuditService {

    private static final Logger logger = LoggerFactory.getLogger(NightAuditServiceImpl.class);

    private final ReservationRepository reservationRepository;
    private final ReservationChambreRepository reservationChambreRepository;
    private final NuiteeRepository nuiteeRepository;
    private final Clock clock;

    public NightAuditServiceImpl(ReservationRepository reservationRepository,
                                 ReservationChambreRepository reservationChambreRepository,
                                 NuiteeRepository nuiteeRepository,
                                 Clock clock) {
        this.reservationRepository = reservationRepository;
        this.reservationChambreRepository = reservationChambreRepository;
        this.nuiteeRepository = nuiteeRepository;
        this.clock = clock;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Algorithme :</p>
     * <ol>
     *   <li><b>NO_SHOW</b> : pour chaque reservation {@code CONFIRMEE} dont
     *       {@code dateArrivee &lt; today}, transition vers {@code NO_SHOW}.
     *       La requete {@code findByStatutAndDateArriveeBefore(CONFIRMEE, today)}
     *       garantit l'idempotence : une reservation deja {@code NO_SHOW} n'est
     *       pas re-prise (filtre sur {@code statut = CONFIRMEE}).</li>
     *   <li><b>Nuitees manquantes</b> : pour chaque reservation {@code ARRIVEE},
     *       parcourir les pivots {@code reservation_chambre} et generer les
     *       nuitees manquantes pour la periode [dateDebut, min(today, dateFin)).
     *       Le test {@code existsByReservationIdAndChambreIdAndDateNuit} (Tour
     *       12bis) garantit l'idempotence : une nuitee deja presente n'est pas
     *       redoublee.</li>
     * </ol>
     *
     * <p><b>Politique no-show</b> : la chambre n'est PAS liberee. Selon le
     * standard hotelier, une reservation no-show est facturee a la nuit non
     * honoree (la chambre etait reservee, donc indisponible pour un walk-in).
     * Le statut de la chambre reste donc inchange par cette methode.</p>
     */
    @Override
    @Transactional
    public NightAuditResultDto run() {
        Long hotelId = TenantContext.get();
        LocalDate today = LocalDate.now(clock);
        logger.info("Night audit demarre : hotelId={}, today={}", hotelId, today);

        int nbNoShow = markNoShowReservations(today);
        int nbNuiteesManquantes = generateMissingNuitees(today);

        Instant executedAt = Instant.now(clock);
        logger.info("Night audit termine : hotelId={}, today={}, nbNoShow={}, nbNuiteesGenerees={}",
                hotelId, today, nbNoShow, nbNuiteesManquantes);

        return new NightAuditResultDto(hotelId, today, nbNoShow, nbNuiteesManquantes, executedAt);
    }

    /**
     * Marque les reservations CONFIRMEE dont la date d'arrivee est passee
     * comme NO_SHOW.
     *
     * @param today date de reference (= today selon le {@link Clock})
     * @return nombre de reservations marquees
     */
    private int markNoShowReservations(LocalDate today) {
        List<Reservation> candidates = reservationRepository
                .findByStatutAndDateArriveeBefore(StatutReservation.CONFIRMEE, today);
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
     * Pour les sejours en cours (statut ARRIVEE), genere les nuitees manquantes
     * sur la periode [dateDebut, min(today, dateFin)) de chaque pivot
     * reservation/chambre.
     *
     * @param today date de reference
     * @return nombre de nuitees creees
     */
    private int generateMissingNuitees(LocalDate today) {
        List<Reservation> active = reservationRepository.findByStatut(StatutReservation.ARRIVEE);
        int count = 0;
        for (Reservation reservation : active) {
            List<ReservationChambre> pivots = reservationChambreRepository
                    .findByReservationIdOrderByDateDebutAsc(reservation.getReservationId());
            for (ReservationChambre pivot : pivots) {
                LocalDate fin = pivot.getDateFin().isBefore(today) ? pivot.getDateFin() : today;
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
                        // Sejour ARRIVEE et nuit deja passee -> CONSOMMEE.
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
}

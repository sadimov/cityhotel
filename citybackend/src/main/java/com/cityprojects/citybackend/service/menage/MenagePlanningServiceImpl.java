package com.cityprojects.citybackend.service.menage;

import com.cityprojects.citybackend.common.tenant.RequireTenant;
import com.cityprojects.citybackend.entity.hebergement.Reservation;
import com.cityprojects.citybackend.entity.hebergement.ReservationChambre;
import com.cityprojects.citybackend.entity.hebergement.StatutReservation;
import com.cityprojects.citybackend.entity.menage.StatutTache;
import com.cityprojects.citybackend.entity.menage.Tache;
import com.cityprojects.citybackend.entity.menage.TypeNettoyage;
import com.cityprojects.citybackend.repository.hebergement.ReservationChambreRepository;
import com.cityprojects.citybackend.repository.hebergement.ReservationRepository;
import com.cityprojects.citybackend.repository.menage.TacheRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Implementation de {@link MenagePlanningService} - Tour 30 (Workflow A).
 *
 * <p>Cross-module : ce service lit
 * {@link com.cityprojects.citybackend.entity.hebergement.Reservation} et
 * {@link com.cityprojects.citybackend.entity.hebergement.ReservationChambre}
 * en lecture seule (filtre tenant Hibernate). Il ecrit dans
 * {@code menage.taches}. Aucun appel sortant vers {@code ChambreService} (la
 * mutation de statut chambre est portee par {@link ChambreStatutListener}
 * Workflows B/C).</p>
 *
 * <h3>Idempotence</h3>
 * <p>{@link #creerTacheCheckOutSiAbsente} est sur-appellable sans risque : un
 * doublon serait refuse par
 * {@link TacheRepository#existsByChambreIdAndDatePlanifieeAndTypeNettoyageAndStatutNot}.
 * Le statut {@code ANNULEE} est exclu de l'unicite : si une tache QUOTIDIEN
 * du jour pour la chambre est ANNULEE, on autorise une nouvelle creation.</p>
 */
@Service
@RequireTenant
@Transactional(readOnly = true)
public class MenagePlanningServiceImpl implements MenagePlanningService {

    private static final Logger logger = LoggerFactory.getLogger(MenagePlanningServiceImpl.class);

    private final TacheRepository tacheRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationChambreRepository reservationChambreRepository;

    public MenagePlanningServiceImpl(TacheRepository tacheRepository,
                                     ReservationRepository reservationRepository,
                                     ReservationChambreRepository reservationChambreRepository) {
        this.tacheRepository = tacheRepository;
        this.reservationRepository = reservationRepository;
        this.reservationChambreRepository = reservationChambreRepository;
    }

    @Override
    @Transactional
    public void creerTacheCheckOutSiAbsente(Long chambreId, LocalDate date) {
        boolean dejaPresente = tacheRepository
                .existsByChambreIdAndDatePlanifieeAndTypeNettoyageAndStatutNot(
                        chambreId, date, TypeNettoyage.QUOTIDIEN, StatutTache.ANNULEE);
        if (dejaPresente) {
            logger.debug("Tache QUOTIDIEN deja presente : chambre={} date={} - skip",
                    chambreId, date);
            return;
        }

        Tache tache = new Tache();
        tache.setChambreId(chambreId);
        tache.setStatut(StatutTache.PLANIFIEE);
        tache.setTypeNettoyage(TypeNettoyage.QUOTIDIEN);
        tache.setDatePlanifiee(date);
        // Priorite 2 : superieure a la valeur par defaut (1) pour signaler que
        // cette tache est generee automatiquement post check-out (rotation
        // chambres). Le manager peut la rebasculer manuellement.
        tache.setPriorite(2);
        // PAS de setHotelId : Hibernate le populate via @TenantId resolver.
        // PAS de personnel : tache non assignee (a faire via assigner()).

        tacheRepository.save(tache);
        logger.info("Tache QUOTIDIEN creee post check-out : chambre={} date={} priorite=2",
                chambreId, date);
    }

    @Override
    @Transactional
    public int genererPlanningDuJour(LocalDate date) {
        // Toutes les reservations PARTIE du tenant courant (filtre Hibernate)
        // dont la date de depart est exactement la date d'analyse.
        List<Reservation> partiesDuJour = reservationRepository
                .findByDateDepartAndStatutOrderByDateDepartAsc(date, StatutReservation.PARTIE);

        int created = 0;
        for (Reservation r : partiesDuJour) {
            List<ReservationChambre> chambres = reservationChambreRepository
                    .findByReservationIdOrderByDateDebutAsc(r.getReservationId());
            for (ReservationChambre rc : chambres) {
                // Compteur incremente UNIQUEMENT si la tache n'existait pas
                // - on s'appuie sur le test d'existence cote service.
                boolean dejaPresente = tacheRepository
                        .existsByChambreIdAndDatePlanifieeAndTypeNettoyageAndStatutNot(
                                rc.getChambreId(), date, TypeNettoyage.QUOTIDIEN, StatutTache.ANNULEE);
                if (dejaPresente) {
                    continue;
                }
                creerTacheCheckOutSiAbsente(rc.getChambreId(), date);
                created++;
            }
        }
        logger.info("Generation planning ménage du {} : {} tache(s) creee(s) sur {} reservation(s) PARTIE",
                date, created, partiesDuJour.size());
        return created;
    }
}

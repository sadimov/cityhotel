package com.cityprojects.citybackend.service.hebergement;

import com.cityprojects.citybackend.dto.hebergement.NightAuditResultDto;

/**
 * Service du night audit (clôture de la journée hôtelière).
 *
 * <p>Cf. {@code regles_night_audit.txt}. Le night audit ne s'exécute pas
 * automatiquement : il est déclenché manuellement par un utilisateur ADMIN
 * ou NIGHTAUDIT via {@code POST /api/hebergement/night-audit/run}. Le
 * {@code NightAuditScheduler} ne fait qu'envoyer les notifications SSE
 * (alerte 3 min + ouverture de modale à midi).</p>
 *
 * <h3>Effets</h3>
 * <ol>
 *   <li>Transition de la journée hôtelière OUVERTE → CLOTURE_EN_COURS via
 *       {@link HotelDayService#startClosure(Long)}. À partir de cet instant,
 *       le {@code NightAuditLockFilter} rejette en 423 toute requête HTTP
 *       modifiante pour ce tenant (hors auth + endpoint /run lui-même).</li>
 *   <li>Marque les réservations {@code CONFIRMEE} dont {@code dateArrivee} est
 *       passée comme {@code NO_SHOW} (idempotent : ne re-marque pas une
 *       réservation déjà {@code NO_SHOW}).</li>
 *   <li>Génère les nuitées manquantes pour les séjours {@code ARRIVEE} en cours
 *       (compense un crash en cours de séjour ou un trou de création — utilise
 *       le marqueur d'idempotence {@code existsByReservationIdAndChambreIdAndDateNuit}).</li>
 *   <li>Transition CLOTURE_EN_COURS → CLOTUREE + ouverture de la journée J+1
 *       via {@link HotelDayService#completeClosure()}. Le verrou HTTP est levé.</li>
 *   <li>En cas d'exception : {@link HotelDayService#abortClosure()} repasse la
 *       journée en OUVERTE, la transaction Spring rollback les effets métier.</li>
 * </ol>
 *
 * <h3>Date hôtelière</h3>
 * <p>Toute la logique métier s'appuie désormais sur la date hôtelière
 * matérialisée (cf. {@link HotelDayService}), pas sur {@code LocalDate.now()}.
 * Conformément aux règles : tant que le NA n'a pas tourné, le système reste
 * sur la même journée hôtelière même si l'horloge serveur a changé de jour.</p>
 *
 * <h3>Hors scope</h3>
 * <ul>
 *   <li>Génération des factures et pièces comptables à partir des nuitées
 *       CONSOMMÉE — externalisé Dolibarr (bridge Feign Vague 3).</li>
 * </ul>
 */
public interface NightAuditService {

    /**
     * Exécute le night audit pour le tenant courant.
     *
     * <p><b>Non idempotent</b> sur la transition de journée : un second appel
     * pendant que la journée est en CLOTURE_EN_COURS retourne 400
     * {@code error.nightAudit.alreadyRunning}. Les effets métier (NO_SHOW,
     * nuitées) restent idempotents en eux-mêmes.</p>
     *
     * @return un résumé des actions effectuées (cf. {@link NightAuditResultDto}).
     *         Le champ {@code dateExecution} contient la date hôtelière qui
     *         vient d'être clôturée — pas {@code LocalDate.now()}.
     */
    NightAuditResultDto run();
}

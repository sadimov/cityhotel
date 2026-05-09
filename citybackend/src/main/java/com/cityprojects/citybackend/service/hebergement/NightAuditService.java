package com.cityprojects.citybackend.service.hebergement;

import com.cityprojects.citybackend.dto.hebergement.NightAuditResultDto;

/**
 * Service du night audit (cloture de la journee hoteliere).
 *
 * <p>Tour 13 - cf. {@code regles_night_audit.txt}. Le night audit ne s'execute
 * pas automatiquement : il est declenche manuellement par un utilisateur ADMIN
 * ou NIGHTAUDIT via {@code POST /api/hebergement/night-audit/run}, ou indirectement via le
 * scheduler ({@code NightAuditScheduler}) qui n'envoie que des notifications.</p>
 *
 * <h3>Effets</h3>
 * <ol>
 *   <li>Marque les reservations {@code CONFIRMEE} dont {@code dateArrivee} est
 *       passee comme {@code NO_SHOW} (idempotent : ne re-marque pas une
 *       reservation deja {@code NO_SHOW}).</li>
 *   <li>Genere les nuitees manquantes pour les sejours {@code ARRIVEE} en cours
 *       (compense un crash en cours de sejour ou un trou de creation - utilise
 *       le marqueur d'idempotence {@code existsByReservationIdAndChambreIdAndDateNuit}).</li>
 * </ol>
 *
 * <h3>Hors scope Tour 13 (a traiter dans un tour ulterieur dedie)</h3>
 * <ul>
 *   <li>Modele {@code JourneeHoteliere} explicite (date hoteliere distincte du calendrier).</li>
 *   <li>Blocage des operations utilisateurs pendant la fenetre 3-min.</li>
 *   <li>Generation des factures et pieces comptables a partir des nuitees CONSOMMEEs.</li>
 * </ul>
 */
public interface NightAuditService {

    /**
     * Execute le night audit pour le tenant courant.
     *
     * <p>Idempotent : peut etre relance plusieurs fois sur la meme journee
     * sans effet de bord supplementaire.</p>
     *
     * @return un resume des actions effectuees
     */
    NightAuditResultDto run();
}

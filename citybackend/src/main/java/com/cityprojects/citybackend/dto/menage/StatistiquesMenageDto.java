package com.cityprojects.citybackend.dto.menage;

import java.time.LocalDate;
import java.util.Map;

/**
 * Statistiques agregees du module menage (sous-tour C).
 *
 * <p>Endpoint cible : {@code GET /api/menage/statistiques} (du jour) +
 * {@code GET /api/menage/statistiques/periode?dateDebut=&dateFin=}.</p>
 *
 * <p>Alignement strict avec le modele frontend
 * {@code cityfrontend/src/app/features/menage/models/statistiques-menage.model.ts}
 * {@code StatistiquesMenage}.</p>
 *
 * <p>Note champs non implementes en V1 :</p>
 * <ul>
 *   <li>{@code nombreConflitsPlanning} : retourne toujours 0 pour l'instant
 *       (logique de detection des conflits planning a affiner).</li>
 * </ul>
 *
 * @param dateReference            date de reference (jour ou debut de periode)
 * @param nombrePersonnelActif     nombre d'agents actifs ce jour-la
 * @param nombreTachesAujourdhui   nombre total de taches sur la periode
 * @param nombreTachesEnCours      taches au statut EN_COURS
 * @param nombreTachesTerminees    taches au statut TERMINEE
 * @param nombreTachesEnRetard     taches non terminees avec date passee
 * @param repartitionParStatut     count par {@code statut.name()}
 * @param repartitionParType       count par {@code typeNettoyage.name()}
 * @param repartitionParPriorite   count par priorite (cle = "1"/"2"/"3")
 * @param tempsRealisationMoyen    moyenne en minutes de
 *                                 {@code (heureFinReelle - heureDebutReelle)}
 *                                 sur les taches TERMINEE de la periode
 * @param tauxRealisationPourcentage  pourcentage TERMINEE / TOTAL
 * @param nombreTachesUrgentes     count des taches priorite = 3 ("Critique")
 * @param nombreConflitsPlanning   non implemente V1 ; renvoie 0
 */
public record StatistiquesMenageDto(
        LocalDate dateReference,
        Long nombrePersonnelActif,
        Long nombreTachesAujourdhui,
        Long nombreTachesEnCours,
        Long nombreTachesTerminees,
        Long nombreTachesEnRetard,
        Map<String, Long> repartitionParStatut,
        Map<String, Long> repartitionParType,
        Map<String, Long> repartitionParPriorite,
        Double tempsRealisationMoyen,
        Double tauxRealisationPourcentage,
        Long nombreTachesUrgentes,
        Long nombreConflitsPlanning) {
}

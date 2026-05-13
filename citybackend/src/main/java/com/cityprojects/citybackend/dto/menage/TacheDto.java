package com.cityprojects.citybackend.dto.menage;

import com.cityprojects.citybackend.entity.menage.StatutTache;
import com.cityprojects.citybackend.entity.menage.TypeNettoyage;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * DTO de sortie pour {@link com.cityprojects.citybackend.entity.menage.Tache}.
 *
 * <p><b>NE CONTIENT PAS</b> {@code hotelId} (resolu via TenantContext cote
 * backend, jamais expose au frontend).</p>
 *
 * <h3>Champs persistes (mapping direct entite -> DTO)</h3>
 * <ul>
 *   <li>{@code tacheId}, {@code chambreId}, {@code personnelId} (FK Long)</li>
 *   <li>{@code statut}, {@code typeNettoyage} (enums)</li>
 *   <li>{@code priorite} (1..3)</li>
 *   <li>{@code datePlanifiee}, {@code heureDebutPrevue}, {@code heureFinPrevue}</li>
 *   <li>{@code heureDebutReelle}, {@code heureFinReelle} (Instant UTC)</li>
 *   <li>{@code commentaires}, {@code problemesDetectes}, {@code materielUtilise}</li>
 *   <li>{@code noteQualite} (1..5, nullable — rempli au moment du terminer)</li>
 *   <li>{@code version} (optimistic lock — necessaire au frontend pour
 *       renvoyer la version sur les transitions assigner/commencer/terminer)</li>
 *   <li>{@code createdAt}, {@code updatedAt} (audit)</li>
 * </ul>
 *
 * <h3>Champs derives (sous-tour menage A2 — mapper enrichi)</h3>
 * <ul>
 *   <li>{@code numeroChambre} : libelle de la chambre cible (jointure
 *       {@code hebergement.chambres}).</li>
 *   <li>{@code nomPersonnel} : nom complet de l'agent assigne
 *       (jointure {@code menage.personnel} — {@code null} si non assigne).</li>
 *   <li>{@code codeStatut} : code enum {@link StatutTache#name()} — pratique
 *       pour les filtres rapides cote front.</li>
 *   <li>{@code libelleStatut} : libelle francais traduit ("Planifiee",
 *       "Assignee", "En cours", "Terminee", "Annulee").</li>
 *   <li>{@code libellePriorite} : "Normale" (1) / "Urgente" (2) / "Critique" (3).</li>
 *   <li>{@code dureeMinutes} : duree d'execution effective
 *       ({@code heureFinReelle - heureDebutReelle} en minutes). {@code null}
 *       si la tache n'est pas encore terminee.</li>
 *   <li>{@code enRetard} : {@code true} si la tache n'est pas terminee et
 *       que {@code datePlanifiee} est passee (ou {@code heureFinPrevue}
 *       depassee aujourd'hui).</li>
 *   <li>{@code enCours} : {@code true} si {@code statut == EN_COURS}.</li>
 *   <li>{@code terminee} : {@code true} si {@code statut == TERMINEE}.</li>
 * </ul>
 *
 * <p>Ces champs derives sont calcules par le mapper MapStruct
 * {@code TacheMapper} (sous-tour menage A2). En cas d'absence — par
 * exemple pendant la phase de transition entre A1 et A2 ou si le mapper
 * n'a pas acces aux repos jointures — ils restent {@code null}, ce qui
 * est tolere cote frontend (cf. modele {@code Tache} avec
 * {@code numeroChambre?: string}).</p>
 */
public record TacheDto(
        Long tacheId,
        Long chambreId,
        Long personnelId,
        StatutTache statut,
        TypeNettoyage typeNettoyage,
        Integer priorite,
        LocalDate datePlanifiee,
        LocalTime heureDebutPrevue,
        LocalTime heureFinPrevue,
        Instant heureDebutReelle,
        Instant heureFinReelle,
        String commentaires,
        String problemesDetectes,
        String materielUtilise,
        Integer noteQualite,
        Long version,
        Instant createdAt,
        Instant updatedAt,
        // Champs derives (renseignes par le mapper en A2) :
        String numeroChambre,
        String nomPersonnel,
        String codeStatut,
        String libelleStatut,
        String libellePriorite,
        Long dureeMinutes,
        Boolean enRetard,
        Boolean enCours,
        Boolean terminee) {
}

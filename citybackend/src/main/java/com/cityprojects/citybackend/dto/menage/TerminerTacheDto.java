package com.cityprojects.citybackend.dto.menage;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * DTO pour terminer une tache (rapport de fin).
 *
 * <p>Tous les champs optionnels : la tache peut etre cloturee sans
 * commentaires si tout s'est bien passe.</p>
 *
 * <p>Format des champs :</p>
 * <ul>
 *   <li>{@code commentaires} : texte libre (max 1000 caracteres).</li>
 *   <li>{@code problemesDetectes} : texte libre (max 1000) — sert
 *       d'entree au workflow maintenance ultérieur.</li>
 *   <li>{@code materielUtilise} : <b>chaîne</b> (max 500 caracteres) — au
 *       choix CSV (« aspirateur,détergent,lingettes ») ou JSON
 *       (« ["aspirateur","détergent"] »). Le frontend sérialise un tableau
 *       en chaîne avant d'envoyer (cf. {@code tache.model.ts} :
 *       {@code materielUtilise: string[]} avec {@code join(',')}).</li>
 *   <li>{@code noteQualite} : note d'évaluation 1..5 (sous-tour menage A1).
 *       Bornée {@code @Min(1) @Max(5)} cote backend + CHECK SQL identique
 *       (changeset 038-add-note-qualite-taches).</li>
 * </ul>
 */
public record TerminerTacheDto(
        @Size(max = 1000, message = "error.tache.commentaires.tooLong")
        String commentaires,

        @Size(max = 1000, message = "error.tache.problemes.tooLong")
        String problemesDetectes,

        @Size(max = 500, message = "error.tache.materiel.tooLong")
        String materielUtilise,

        @Min(value = 1, message = "error.tache.noteQualite.min")
        @Max(value = 5, message = "error.tache.noteQualite.max")
        Integer noteQualite) {
}

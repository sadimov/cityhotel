package com.cityprojects.citybackend.dto.menage;

import jakarta.validation.constraints.Size;

/**
 * DTO pour terminer une tache (rapport de fin).
 *
 * <p>Tous les champs optionnels : la tache peut etre cloturee sans
 * commentaires si tout s'est bien passe.</p>
 */
public record TerminerTacheDto(
        @Size(max = 1000, message = "error.tache.commentaires.tooLong")
        String commentaires,

        @Size(max = 1000, message = "error.tache.problemes.tooLong")
        String problemesDetectes,

        @Size(max = 500, message = "error.tache.materiel.tooLong")
        String materielUtilise) {
}

package com.cityprojects.citybackend.entity.menage;

/**
 * Cycle de vie d'une tache de menage.
 *
 * <h3>Decision (Tour 27)</h3>
 * <p>Le brief utilisateur initial mentionnait des valeurs PROPRE / SALE /
 * EN_COURS / BLOQUEE. L'analyse du mono
 * {@code MENAGE/entities_dto_services_backend-menage.java} a montre que ces
 * valeurs designent en realite l'etat de proprete de la <i>chambre</i>
 * (orthogonal au {@link com.cityprojects.citybackend.entity.hebergement.StatutChambre}
 * qui couvre l'occupation), pas le cycle d'une tache.</p>
 *
 * <p>Le mono utilise les transitions {@code "PLANIFIEE" -> "EN_COURS" -> "TERMINEE"}
 * dans {@code historiqueService.enregistrerAction()} - ces valeurs sont retenues
 * ici, plus {@link #ANNULEE} pour couvrir la suppression metier (vs DELETE
 * physique).</p>
 *
 * <p>Aucun champ {@code statutChambre} n'est ajoute ce tour : le menage ne
 * touche pas au statut de la chambre cote hebergement (Tour 11). Si une
 * automatisation est requise plus tard (post-tache TERMINEE -> chambre
 * DISPONIBLE), elle se fera via un service applicatif depuis le menage vers
 * {@code ChambreService}, pas via une mutation directe en base.</p>
 *
 * <p>Stocke en base sous forme de VARCHAR (cf. changeset 007-m.3).</p>
 */
public enum StatutTache {
    /** Tache creee, pas encore commencee. Equivalent "TODO". */
    PLANIFIEE,
    /** Tache demarree (heureDebutReelle renseignee, heureFinReelle nulle). */
    EN_COURS,
    /** Tache terminee (heureDebutReelle et heureFinReelle renseignees). */
    TERMINEE,
    /** Tache annulee metier (alternative au DELETE physique). */
    ANNULEE
}

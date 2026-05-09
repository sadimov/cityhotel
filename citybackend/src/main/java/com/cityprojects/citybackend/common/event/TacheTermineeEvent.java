package com.cityprojects.citybackend.common.event;

import com.cityprojects.citybackend.entity.menage.TypeNettoyage;

/**
 * Event applicatif Spring publie apres COMMIT de la fin d'une tache de menage
 * (Tour 30 - Workflow B : marquer chambre DISPONIBLE apres QUOTIDIEN /
 * GRAND_MENAGE / MAINTENANCE TERMINEE).
 *
 * <p>Consomme par {@link com.cityprojects.citybackend.service.menage.ChambreStatutListener}
 * qui :
 * <ul>
 *   <li>QUOTIDIEN ou GRAND_MENAGE -&gt; chambre NETTOYAGE -&gt; DISPONIBLE.</li>
 *   <li>MAINTENANCE -&gt; chambre MAINTENANCE -&gt; DISPONIBLE.</li>
 * </ul>
 *
 * <h3>Cycle de vie</h3>
 * <p>Publie depuis
 * {@link com.cityprojects.citybackend.service.menage.TacheServiceImpl#terminer(Long, com.cityprojects.citybackend.dto.menage.TerminerTacheDto)}
 * apres {@code save(tache)}. Listener AFTER_COMMIT + REQUIRES_NEW.</p>
 *
 * <h3>Resilience</h3>
 * <p>Le listener log WARN sans rethrow si la transition de chambre est refusee
 * (ex. chambre deja DISPONIBLE par un autre canal, ou statut imprevu) : un
 * incident sur la chambre ne doit pas faire crasher la TX qui a pose la fin
 * de tache.</p>
 */
public record TacheTermineeEvent(
        Long tacheId,
        Long chambreId,
        Long hotelId,
        TypeNettoyage typeNettoyage) {
}

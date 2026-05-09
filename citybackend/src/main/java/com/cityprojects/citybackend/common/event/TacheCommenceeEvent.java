package com.cityprojects.citybackend.common.event;

import com.cityprojects.citybackend.entity.menage.TypeNettoyage;

/**
 * Event applicatif Spring publie apres COMMIT du debut d'une tache de menage
 * (Tour 30 - Workflow C : blocage chambre lors d'une MAINTENANCE EN_COURS).
 *
 * <p>Consomme par {@link com.cityprojects.citybackend.service.menage.ChambreStatutListener}
 * pour faire passer la chambre en MAINTENANCE quand
 * {@code typeNettoyage == MAINTENANCE}. Les autres types (QUOTIDIEN,
 * GRAND_MENAGE) sont no-op au demarrage : la chambre est deja en NETTOYAGE
 * via le checkOut.</p>
 *
 * <h3>Cycle de vie</h3>
 * <p>Publie depuis
 * {@link com.cityprojects.citybackend.service.menage.TacheServiceImpl#commencer(Long)}
 * apres {@code save(tache)}. Listener AFTER_COMMIT + REQUIRES_NEW.</p>
 */
public record TacheCommenceeEvent(
        Long tacheId,
        Long chambreId,
        Long hotelId,
        TypeNettoyage typeNettoyage) {
}

package com.cityprojects.citybackend.entity.hebergement;

/**
 * Cycle de vie d'une journée hôtelière (cf. {@code règles_night_audit.txt} §1).
 *
 * <h3>Transitions valides</h3>
 * <ul>
 *   <li>{@code OUVERTE} → {@code CLOTURE_EN_COURS} : démarrage du night audit
 *       (admin clique « Lancer la clôture »).</li>
 *   <li>{@code CLOTURE_EN_COURS} → {@code CLOTUREE} : night audit terminé avec
 *       succès. La journée suivante (J+1) est ouverte dans la même transaction.</li>
 *   <li>{@code CLOTURE_EN_COURS} → {@code OUVERTE} : rollback en cas d'erreur
 *       métier pendant l'exécution du night audit (abort).</li>
 * </ul>
 *
 * <p>État terminal : {@code CLOTUREE} (aucune transition sortante).</p>
 *
 * <h3>Garde HTTP write</h3>
 * <p>Tant que la journée courante est en {@code CLOTURE_EN_COURS}, le
 * {@code NightAuditLockFilter} rejette toute requête HTTP modifiante (POST,
 * PUT, PATCH, DELETE) hors auth et endpoint d'exécution du NA lui-même.</p>
 */
public enum EtatJourneeHoteliere {
    OUVERTE,
    CLOTURE_EN_COURS,
    CLOTUREE
}

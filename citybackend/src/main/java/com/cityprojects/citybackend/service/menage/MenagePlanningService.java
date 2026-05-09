package com.cityprojects.citybackend.service.menage;

import java.time.LocalDate;

/**
 * Service de generation du planning ménage - Tour 30 (Workflow A).
 *
 * <p>Couvre deux entrees :
 * <ul>
 *   <li>{@link #creerTacheCheckOutSiAbsente(Long, LocalDate)} : declenchement
 *       individuel par chambre (utilise par {@link MenagePlanningEventListener}
 *       en reaction a un check-out).</li>
 *   <li>{@link #genererPlanningDuJour(LocalDate)} : balayage cross-reservations
 *       du tenant courant pour rattraper d'eventuels check-out passes inapercus
 *       (endpoint manuel + scheduler 12:05).</li>
 * </ul>
 *
 * <h3>Multi-tenant</h3>
 * <p>Toutes les methodes operent dans le {@code TenantContext} courant
 * (filtre Hibernate {@code @TenantId} actif). L'implementation porte
 * {@code @RequireTenant} (cf. citybackend/CLAUDE.md §3.3).</p>
 */
public interface MenagePlanningService {

    /**
     * Cree (si absente) une tache QUOTIDIEN PLANIFIEE pour la chambre et la
     * date donnees. Idempotent : pas de doublon si une tache du meme couple
     * existe deja avec un statut != ANNULEE.
     *
     * @param chambreId chambre concernee (validee par Hibernate via @TenantId)
     * @param date      date planifiee (typiquement le jour du check-out)
     */
    void creerTacheCheckOutSiAbsente(Long chambreId, LocalDate date);

    /**
     * Genere les taches QUOTIDIEN manquantes pour toutes les chambres dont
     * une reservation est passee en statut PARTIE avec {@code dateCheckOut == date}
     * dans le tenant courant.
     *
     * <p>Utile pour rattraper un cas degrade (event listener manque, panne,
     * redeploiement entre publish et commit). L'idempotence est assuree par
     * {@link #creerTacheCheckOutSiAbsente}.</p>
     *
     * @param date date d'analyse (typiquement {@code LocalDate.now(clock)})
     * @return nombre de taches creees
     */
    int genererPlanningDuJour(LocalDate date);
}

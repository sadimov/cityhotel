package com.cityprojects.citybackend.service.hebergement;

import com.cityprojects.citybackend.entity.hebergement.JourneeHoteliere;

import java.time.LocalDate;

/**
 * Cycle de vie de la « journée hôtelière » du tenant courant (cf.
 * {@code règles_night_audit.txt} §1).
 *
 * <p>Toute logique qui dépendait jusqu'ici de {@code LocalDate.now()} pour
 * borner « aujourd'hui » côté métier hôtelier doit progressivement migrer
 * vers {@link #currentDate()} pour rester cohérente : tant que le night audit
 * n'a pas tourné, la date hôtelière reste sur le jour de la dernière
 * ouverture.</p>
 *
 * <h3>États et transitions</h3>
 * <pre>
 *   OUVERTE ─── startClosure(userId) ──▶ CLOTURE_EN_COURS ─── completeClosure() ──▶ CLOTUREE
 *                                                            (ouvre J+1 OUVERTE)
 *
 *   CLOTURE_EN_COURS ─── abortClosure() ──▶ OUVERTE (rollback en cas d'échec NA)
 * </pre>
 *
 * <h3>Bootstrap</h3>
 * <p>Le premier appel à {@link #currentDay()} pour un hôtel sans aucune journée
 * matérialisée crée une journée OUVERTE à la date du jour calendaire. Pas de
 * seed Liquibase nécessaire.</p>
 */
public interface HotelDayService {

    /**
     * Retourne la journée hôtelière active du tenant courant, en la créant
     * (état OUVERTE, dateHotel = {@code LocalDate.now(clock)}) si elle
     * n'existe pas encore.
     *
     * <p>Lecture / écriture transactionnelle. Sera appelée fréquemment :
     * pour optimiser plus tard si nécessaire, un cache court (clé tenant)
     * peut être ajouté ; pour le moment chaque appel = 1 SELECT.</p>
     */
    JourneeHoteliere currentDay();

    /** Raccourci pour {@code currentDay().getDateHotel()}. */
    LocalDate currentDate();

    /**
     * Démarrage du night audit : transition OUVERTE → CLOTURE_EN_COURS.
     * <p>Verrou pessimiste sur la ligne + bump @Version. Si la journée est
     * déjà CLOTURE_EN_COURS, lève {@code IllegalStateException} (i18n
     * {@code error.nightAudit.alreadyRunning}).</p>
     *
     * @param closedByUserId user à enregistrer comme déclencheur (peut être null)
     * @return la journée transitionnée
     */
    JourneeHoteliere startClosure(Long closedByUserId);

    /**
     * Fin de night audit avec succès : transition CLOTURE_EN_COURS → CLOTUREE,
     * et ouverture de la journée J+1 (état OUVERTE) dans la même transaction.
     *
     * @return la journée qui vient d'être CLOTUREE (pour récup du
     *         {@code dateHotel} qu'on inclut dans le DTO de réponse)
     */
    JourneeHoteliere completeClosure();

    /**
     * Rollback du night audit (exception métier pendant {@code NightAuditService.run()}) :
     * transition CLOTURE_EN_COURS → OUVERTE. Préserve la journée courante.
     */
    JourneeHoteliere abortClosure();
}

package com.cityprojects.citybackend.common.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marqueur AOP pour les flux financiers et comptables (Bloc B3).
 *
 * <p>L'aspect {@link AuditFinanceActionAspect} pose une ligne dans
 * {@code finance.audit_finance_log} apres execution reussie : action,
 * entityType, entityId resolu depuis le premier parametre {@code Long}
 * ou l'id du DTO retourne, userId via {@code SecurityContextHolder},
 * timestamp serveur.</p>
 *
 * <h3>Pourquoi pas {@link AuditAction} ?</h3>
 * <p>L'aspect existant ({@link AuditActionAspect}) du module menage est
 * fortement couple a {@code Tache}/{@code TacheDto}/{@code HistoriqueService}
 * (lookup pre-execution, reading de {@code tacheId, chambreId, personnelId, statut}).
 * Le brancher sur les services finance demanderait soit un fork (probabilite
 * elevee de regression menage), soit une refonte importante hors scope B3.
 * On introduit donc un aspect dedie ; les deux peuvent cohabiter sans
 * collision (annotations distinctes, beans distincts).</p>
 *
 * <h3>Contrat sur la methode annotee</h3>
 * <ul>
 *   <li>{@link #entityType()} : libelle court de l'entite (FACTURE, PAIEMENT,
 *       ECRITURE, EXERCICE, BON_COMMANDE, BON_SORTIE...).</li>
 *   <li>{@link #value()} : libelle de l'action ({@code FACTURE_EMISSION},
 *       {@code PAIEMENT_CREATION}, etc.). Convention SCREAMING_SNAKE_CASE.</li>
 *   <li>L'entityId est resolu :
 *       <ol>
 *         <li>depuis le premier argument {@code Long} de la methode (cas
 *             {@code annuler(Long id)}, {@code contrePasser(Long id, ...)}) ;</li>
 *         <li>sinon depuis le DTO retourne via reflection ({@code id()},
 *             {@code factureId()}, {@code paiementId()}...).</li>
 *       </ol>
 *   </li>
 * </ul>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditFinanceAction {

    /**
     * Libelle de l'action (ex. {@code "FACTURE_EMISSION"},
     * {@code "PAIEMENT_ANNULATION"}, {@code "ECRITURE_CONTREPASSATION"}).
     */
    String value();

    /**
     * Type metier de l'entite cible (ex. {@code "FACTURE"}, {@code "PAIEMENT"},
     * {@code "ECRITURE"}, {@code "EXERCICE"}, {@code "BON_COMMANDE"},
     * {@code "BON_SORTIE"}). Stocke dans la colonne {@code entity_type}.
     */
    String entityType();
}

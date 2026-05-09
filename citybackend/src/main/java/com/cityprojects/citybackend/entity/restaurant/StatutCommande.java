package com.cityprojects.citybackend.entity.restaurant;

/**
 * Cycle de vie d'une commande POS restaurant (Tour 24).
 *
 * <h3>Transitions valides</h3>
 * <ul>
 *   <li>{@code BROUILLON} -&gt; {@code VALIDEE} : envoi en cuisine (ticket
 *       cuisine imprime).</li>
 *   <li>{@code BROUILLON} -&gt; {@code ANNULEE} : annulation avant envoi.</li>
 *   <li>{@code VALIDEE} -&gt; {@code EN_PREPARATION} : prise en charge par la
 *       cuisine.</li>
 *   <li>{@code EN_PREPARATION} -&gt; {@code PRETE} : commande prete a etre
 *       servie.</li>
 *   <li>{@code PRETE} -&gt; {@code SERVIE} : livraison au client (declenche
 *       potentiellement les BS inventory - TODO[tour-bs-consommations]).</li>
 *   <li>{@code BROUILLON} / {@code VALIDEE} / {@code EN_PREPARATION} /
 *       {@code PRETE} -&gt; {@code ANNULEE} : annulation avec motif.</li>
 *   <li>{@code SERVIE} : etat terminal, ne peut plus etre annule.</li>
 * </ul>
 *
 * <p>Le reglement (encaissement comptant ou report sur chambre) est decorrele
 * de l'etat de preparation : une commande peut etre encaissee avant ou apres
 * la livraison selon la doctrine POS du restaurant.</p>
 */
public enum StatutCommande {

    /** Commande en cours de saisie au POS (modifiable). */
    BROUILLON,

    /** Commande validee, envoyee en cuisine. */
    VALIDEE,

    /** Cuisine en preparation. */
    EN_PREPARATION,

    /** Commande prete a etre servie. */
    PRETE,

    /** Commande servie au client (etat terminal). */
    SERVIE,

    /** Commande annulee (etat terminal, motif obligatoire). */
    ANNULEE
}

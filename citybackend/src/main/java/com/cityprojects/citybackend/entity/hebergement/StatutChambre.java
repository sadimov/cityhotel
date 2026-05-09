package com.cityprojects.citybackend.entity.hebergement;

/**
 * Statut operationnel d'une chambre.
 *
 * <h3>Transitions valides</h3>
 * <ul>
 *   <li>{@code DISPONIBLE} -&gt; {@code OCCUPEE} : check-in d'une reservation.</li>
 *   <li>{@code OCCUPEE} -&gt; {@code NETTOYAGE} : check-out (housekeeping a faire).</li>
 *   <li>{@code NETTOYAGE} -&gt; {@code DISPONIBLE} : nettoyage termine.</li>
 *   <li>{@code DISPONIBLE} -&gt; {@code MAINTENANCE} : intervention technique programmee.</li>
 *   <li>{@code MAINTENANCE} -&gt; {@code DISPONIBLE} : intervention terminee.</li>
 *   <li>{@code MAINTENANCE} -&gt; {@code HORS_SERVICE} : indisponible duree indeterminee.</li>
 *   <li>{@code HORS_SERVICE} -&gt; {@code MAINTENANCE} : remise en condition (pas de
 *       retour direct vers {@code DISPONIBLE}).</li>
 * </ul>
 *
 * <p>Transitions interdites : {@code OCCUPEE} -&gt; {@code MAINTENANCE} (impossible
 * tant qu'un client est dedans), {@code OCCUPEE} -&gt; {@code DISPONIBLE} sans
 * passer par {@code NETTOYAGE} (regle housekeeping).</p>
 */
public enum StatutChambre {
    DISPONIBLE,
    OCCUPEE,
    NETTOYAGE,
    MAINTENANCE,
    HORS_SERVICE
}

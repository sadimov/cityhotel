package com.cityprojects.citybackend.entity.hebergement;

/**
 * Statut d'une nuitee (1 nuit / 1 chambre / 1 reservation).
 *
 * <h3>Transitions valides</h3>
 * <ul>
 *   <li>{@code PREVUE} : creee a la confirmation de la reservation, en attente.</li>
 *   <li>{@code PREVUE} -&gt; {@code CONSOMMEE} : la nuit est passee (post check-in).</li>
 *   <li>{@code CONSOMMEE} -&gt; {@code FACTUREE} : la nuitee est rattachee a une
 *       ligne de facture (Tour 19, via {@code FactureService.fromReservation()}).</li>
 * </ul>
 *
 * <p>Pas de transition retour. Une nuitee FACTUREE figee permet l'audit comptable.</p>
 */
public enum StatutNuitee {
    PREVUE,
    CONSOMMEE,
    FACTUREE
}

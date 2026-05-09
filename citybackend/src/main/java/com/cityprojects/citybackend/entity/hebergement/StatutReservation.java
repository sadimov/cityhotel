package com.cityprojects.citybackend.entity.hebergement;

/**
 * Cycle de vie d'une reservation.
 *
 * <h3>Transitions valides</h3>
 * <ul>
 *   <li>{@code CONFIRMEE} -&gt; {@code ARRIVEE} : check-in effectue (date_arrivee atteinte).</li>
 *   <li>{@code CONFIRMEE} -&gt; {@code ANNULEE} : annulation (motif obligatoire) avant arrivee.</li>
 *   <li>{@code CONFIRMEE} -&gt; {@code NO_SHOW} : night audit Tour 13, dateArrivee depassee
 *       sans check-in (la chambre reste consommee/facturee selon politique no-show).</li>
 *   <li>{@code ARRIVEE} -&gt; {@code PARTIE} : check-out effectue.</li>
 *   <li>{@code ARRIVEE} -&gt; {@code ANNULEE} : annulation exceptionnelle (politique
 *       hotel) - libere les chambres si etait ARRIVEE.</li>
 * </ul>
 *
 * <p>Etats terminaux : {@code PARTIE}, {@code ANNULEE} et {@code NO_SHOW}
 * (aucune transition sortante autorisee).</p>
 *
 * <p><b>Note Tour 12bis</b> : la valeur {@code EN_ATTENTE} a ete retiree le 2026-05-05 :
 * {@link com.cityprojects.citybackend.service.hebergement.ReservationServiceImpl#create}
 * force {@code CONFIRMEE} a la creation, et aucun workflow d'approbation n'existe.
 * Si un tel workflow doit etre introduit, retablir la valeur via un changeset additif.</p>
 *
 * <p><b>Note Tour 13</b> : ajout de {@code NO_SHOW}. Conformement a la politique
 * no-show classique, la chambre reservee est consideree consommee (facturee a la
 * nuit non honoree) et n'est PAS automatiquement liberee par le night audit.
 * Stockage : la colonne {@code statut} est {@code VARCHAR(20)} (003-create-hebergement-schema.xml),
 * la valeur {@code NO_SHOW} (7 caracteres) y rentre sans changeset additionnel.</p>
 */
public enum StatutReservation {
    CONFIRMEE,
    ARRIVEE,
    PARTIE,
    ANNULEE,
    NO_SHOW
}

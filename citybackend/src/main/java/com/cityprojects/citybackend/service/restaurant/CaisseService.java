package com.cityprojects.citybackend.service.restaurant;

import com.cityprojects.citybackend.dto.restaurant.ClotureCaisseDto;

import java.time.LocalDate;

/**
 * Service de cloture de caisse journaliere ("Z de caisse", Tour 26.1).
 *
 * <p>Decision arbitree (3=i) : agregat en LECTURE SEULE a la demande, AUCUNE
 * persistance. Si on veut historiser plus tard, ce sera une nouvelle table
 * {@code restaurant.clotures_caisse} avec un service dedie.</p>
 */
public interface CaisseService {

    /**
     * Calcule l'etat de caisse pour une journee donnee, dans le tenant courant.
     *
     * <p>Filtre les paiements {@code VALIDE} dont {@code datePaiement = date},
     * agrege par {@link com.cityprojects.citybackend.entity.finance.ModePaiement}
     * et compte les commandes du jour (encaissees / annulees) pour le tableau
     * de bord serveur.</p>
     *
     * @param date jour observe (heure locale serveur, mais comme {@code datePaiement}
     *             est un {@code LocalDate}, pas de probleme TZ)
     * @return DTO immuable avec les totaux et compteurs
     */
    ClotureCaisseDto statsJournalieres(LocalDate date);
}

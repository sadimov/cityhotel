package com.cityprojects.citybackend.service.restaurant;

import com.cityprojects.citybackend.dto.restaurant.TicketDto;
import com.cityprojects.citybackend.entity.restaurant.TypeTicket;

import java.util.List;

/**
 * Service metier des tickets POS (Tour 24).
 *
 * <p>Trace les impressions de tickets caisse / cuisine et les reimpressions.
 * Le rendu (PDF/PNG) est delegue au front - cette couche ne stocke que la
 * trace pour audit.</p>
 */
public interface TicketService {

    /**
     * Imprime un ticket caisse pour une commande. Echoue si la commande n'existe
     * pas dans le tenant courant.
     */
    TicketDto imprimerTicketCaisse(Long commandeId);

    /**
     * Imprime un bon cuisine pour une commande (pour le passe).
     */
    TicketDto imprimerTicketCuisine(Long commandeId);

    /**
     * Reimprime un ticket existant (CAISSE ou CUISINE). Necessite un motif
     * obligatoire (audit caisse).
     */
    TicketDto reimprimer(Long commandeId, TypeTicket typeOrigine, String motif);

    /**
     * Liste les tickets emis pour une commande, plus recents d'abord.
     */
    List<TicketDto> listerParCommande(Long commandeId);
}

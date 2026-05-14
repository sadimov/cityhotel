package com.cityprojects.citybackend.service.restaurant;

import com.cityprojects.citybackend.dto.restaurant.CommandeCreateDto;
import com.cityprojects.citybackend.dto.restaurant.CommandeDto;
import com.cityprojects.citybackend.dto.restaurant.EncaissementCommandeDto;
import com.cityprojects.citybackend.dto.restaurant.LigneCommandeCreateDto;
import com.cityprojects.citybackend.entity.restaurant.StatutCommande;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Service metier des commandes POS restaurant (Tour 24).
 *
 * <p>Toutes les methodes operent sous {@code @RequireTenant} (TenantContext
 * obligatoire).</p>
 */
public interface CommandeService {

    /**
     * Cree une commande dans le tenant courant. Genere le numero COMM-{exercice}-{pays}-{seq}
     * via NumerotationService. Snapshote {@code libelle}/{@code prixUnitaire}
     * depuis le catalogue ArticleMenu pour chaque ligne.
     */
    CommandeDto create(CommandeCreateDto dto);

    CommandeDto findById(Long commandeId);

    Page<CommandeDto> findByStatut(StatutCommande statut, Pageable pageable);

    /**
     * Transition d'etat de preparation. Refuse les transitions invalides
     * (lever une {@code BusinessException} avec cle {@code error.commande.statut.invalide}).
     */
    CommandeDto changeStatut(Long commandeId, StatutCommande nouveauStatut);

    /**
     * Annule une commande (motif obligatoire). Refuse SERVIE (etat terminal).
     */
    CommandeDto annuler(Long commandeId, String motif);

    /**
     * Encaisse une commande COMPTANT : delegue a FactureService.fromCommande()
     * pour creer la Facture (avec lignes COMMANDE) et au PaiementService pour
     * creer le Paiement avec affectation directe. Met a jour
     * {@code commande.factureId} et {@code commande.montantPaye}.
     *
     * <p>Pre-conditions : {@code modeReglement = COMPTANT}, statut != ANNULEE,
     * pas deja facturee.</p>
     */
    CommandeDto encaisserComptant(Long commandeId, EncaissementCommandeDto dto);

    /**
     * Tour 50 : commandes liees a une reservation (mode REPORTE_CHAMBRE).
     * Utilise par la fiche reservation pour afficher l'historique des plats
     * reportes sur la note de la chambre. Triees par date ascendante.
     */
    List<CommandeDto> findByReservation(Long reservationId);

    /**
     * Tour 50 : commandes d'un client (toutes confondues), triees par date
     * descendante. Walk-in (clientId null) non concerne.
     */
    Page<CommandeDto> findByClient(Long clientId, Pageable pageable);

    /**
     * Tour 50 : ajoute une ligne a une commande deja existante. Refuse si la
     * commande n'est pas en {@code BROUILLON} (cle {@code error.commande.ligne.ajoutInterdit})
     * ou si elle est deja facturee.
     */
    CommandeDto addLigne(Long commandeId, LigneCommandeCreateDto ligneDto);

    /**
     * Tour 50 : retire (delete physique) une ligne tant que la commande est
     * en {@code BROUILLON} (avant envoi cuisine). Refuse si statut != BROUILLON
     * (utiliser {@link #annulerLigne(Long, Long, String)} avec motif apres envoi).
     * Recalcule les montants de la commande.
     */
    CommandeDto removeLigne(Long commandeId, Long ligneId);

    /**
     * Tour 50 : annule une ligne avec motif (apres envoi en cuisine, ex. rupture).
     * Permet de retirer un plat impossible a preparer sans annuler la commande
     * entiere. Persiste un libelle audit dans {@code notesCuisine} ("ANNULEE: motif")
     * puis supprime la ligne et recalcule les montants.
     *
     * <p>Refus :</p>
     * <ul>
     *   <li>Commande {@code SERVIE} ou {@code ANNULEE} -&gt; etat terminal.</li>
     *   <li>Commande deja facturee ({@code factureId != null}).</li>
     *   <li>Suppression de la derniere ligne (utiliser annuler complete).</li>
     * </ul>
     */
    CommandeDto annulerLigne(Long commandeId, Long ligneId, String motif);
}

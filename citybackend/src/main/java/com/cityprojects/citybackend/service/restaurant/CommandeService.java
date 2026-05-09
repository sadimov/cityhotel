package com.cityprojects.citybackend.service.restaurant;

import com.cityprojects.citybackend.dto.restaurant.CommandeCreateDto;
import com.cityprojects.citybackend.dto.restaurant.CommandeDto;
import com.cityprojects.citybackend.dto.restaurant.EncaissementCommandeDto;
import com.cityprojects.citybackend.entity.restaurant.StatutCommande;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

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
}

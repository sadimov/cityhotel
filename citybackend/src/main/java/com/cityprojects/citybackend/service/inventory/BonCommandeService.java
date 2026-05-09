package com.cityprojects.citybackend.service.inventory;

import com.cityprojects.citybackend.dto.inventory.BonCommandeCreateDto;
import com.cityprojects.citybackend.dto.inventory.BonCommandeDto;
import com.cityprojects.citybackend.dto.inventory.ReceptionBonCommandeDto;
import com.cityprojects.citybackend.entity.inventory.StatutBonCommande;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service de gestion des bons de commande fournisseur.
 *
 * <p>Workflow :
 * <ol>
 *   <li>{@link #create(BonCommandeCreateDto)} : BC en {@code BROUILLON}.</li>
 *   <li>{@link #changerStatut(Long, StatutBonCommande)} : transitions BROUILLON -&gt; ENVOYE -&gt; CONFIRME.</li>
 *   <li>{@link #receptionner(Long, ReceptionBonCommandeDto)} : reception (totale ou partielle),
 *       genere les MouvementStock ENTREE et incremente {@code Produit.stockActuel}.</li>
 *   <li>{@link #annuler(Long)} : ANNULE (sauf si deja RECU_COMPLET ou ANNULE).</li>
 * </ol>
 */
public interface BonCommandeService {

    BonCommandeDto create(BonCommandeCreateDto dto);

    BonCommandeDto findById(Long bonCommandeId);

    Page<BonCommandeDto> findByStatut(StatutBonCommande statut, Pageable pageable);

    BonCommandeDto changerStatut(Long bonCommandeId, StatutBonCommande nouveauStatut);

    /**
     * Receptionne (totalement ou partiellement) les marchandises d'un BC.
     *
     * <p>Pour chaque ligne {@code lignes[i]} fournie, ajoute {@code quantiteRecue}
     * a la quantite cumulee. Le {@code Produit.stockActuel} de chaque produit
     * concerne est incremente du delta. Un MouvementStock {@code ENTREE} est
     * cree par produit-ligne. Le statut du BC passe a {@code RECU_PARTIEL} si
     * au moins une ligne reste incomplete, sinon {@code RECU_COMPLET}.</p>
     */
    BonCommandeDto receptionner(Long bonCommandeId, ReceptionBonCommandeDto reception);

    BonCommandeDto annuler(Long bonCommandeId);
}

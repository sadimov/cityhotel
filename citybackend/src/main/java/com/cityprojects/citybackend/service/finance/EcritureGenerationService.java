package com.cityprojects.citybackend.service.finance;

import com.cityprojects.citybackend.entity.finance.Facture;
import com.cityprojects.citybackend.entity.finance.Paiement;
import com.cityprojects.citybackend.entity.inventory.BonCommande;
import com.cityprojects.citybackend.entity.inventory.BonSortie;

import java.math.BigDecimal;

/**
 * Generation d'ecritures comptables a partir d'evenements metier (Bloc B3).
 *
 * <p>Isole la logique de mapping {@code (TypeLigneFacture | ModePaiement | ...) ->
 * TypeEvenementComptable -> compteCode} et la construction du
 * {@link com.cityprojects.citybackend.dto.finance.EcritureComptableCreateDto}
 * approprie, puis appelle {@link EcritureComptableService#creer} pour
 * persister.</p>
 *
 * <p>Toutes les operations sont tenant-scopees ({@code @RequireTenant}).</p>
 *
 * <h3>Choix de design</h3>
 * <p>Service dedie plutot que methodes privees disseminees dans les 4-5
 * services metier (Facture, Paiement, BonCommande, BonSortie) :</p>
 * <ul>
 *   <li>elimine 5 copies de la table de mapping {@code TypeLigneFacture ->
 *       TypeEvenementComptable} et {@code ModePaiement -> journal/compte} ;</li>
 *   <li>centralise la regle TVA (B4 brachera ici) ;</li>
 *   <li>facilite l'audit cross-flux dans
 *       {@code EcritureComptableMultiTenancyIT} et les futurs ITs ;</li>
 *   <li>pas de cycle de dependance (les services metier appellent ce service,
 *       qui n'appelle que {@link EcritureComptableService} et
 *       {@link CompteMappingService}).</li>
 * </ul>
 */
public interface EcritureGenerationService {

    /**
     * Genere et persiste l'ecriture VENTE associee a l'emission d'une facture
     * standard. Journal {@code VTE}, references
     * {@link com.cityprojects.citybackend.entity.finance.TypeLigneFacture}
     * pour ventiler le credit produit, total TTC sur le compte client
     * (411xxx) au debit.
     *
     * <p>No-op (retourne {@code null}) si la facture n'a pas de lignes ou si
     * son montant TTC est nul. Lever une exception ferait rollback de
     * l'emission - on prefere ne pas generer d'ecriture pour les factures
     * "vides" et laisser le service appelant decider.</p>
     *
     * @param facture facture EMISE (statut deja transitionne)
     * @return id de l'ecriture creee, ou {@code null} si rien a comptabiliser
     */
    Long emettreEcritureFacture(Facture facture);

    /**
     * Genere et persiste l'ecriture TRESORERIE associee a un paiement.
     * Journal {@code CAI} ou {@code BAN} selon le mode de paiement, debit
     * sur le compte de tresorerie, credit sur le compte client (411xxx).
     *
     * <p>Mode espece / wallet mobile -&gt; {@code CAI}. Mode CB / cheque /
     * virement -&gt; {@code BAN}.</p>
     *
     * @param paiement paiement persiste (statut VALIDE)
     * @param clientId id du client a rattacher au compte 411xxx (null
     *                  -&gt; CLIENT_PARTICULIER par defaut)
     * @param societeId id de la societe (null si particulier)
     * @return id de l'ecriture creee
     */
    Long emettreEcritureEncaissement(Paiement paiement, Long clientId, Long societeId);

    /**
     * Genere et persiste l'ecriture ACHAT associee a la reception d'un bon
     * de commande. Journal {@code ACH}, debit 311xxx
     * ({@code STOCK_MARCHANDISES}), credit 401xxx ({@code FOURNISSEUR_ORDINAIRE}).
     *
     * @param bc bon de commande receptionne
     * @param montantReception montant total valorise des lignes nouvellement
     *                          receptionnees (somme des qte*pu, hors TVA - la TVA
     *                          arrivera en B4)
     * @return id de l'ecriture creee, ou {@code null} si {@code montantReception}
     *          est nul (cas defensif)
     */
    Long emettreEcritureReceptionBC(BonCommande bc, BigDecimal montantReception);

    /**
     * Genere et persiste l'ecriture de consommation interne associee a la
     * livraison d'un bon de sortie. Journal {@code OD}, debit 601xxx
     * ({@code ACHAT_MARCHANDISES}), credit 311xxx ({@code STOCK_MARCHANDISES}).
     *
     * @param bs bon de sortie livre
     * @param montantSortie valeur totale des produits sortis (somme des
     *                       qte*prixUnitaireProduit)
     * @return id de l'ecriture creee, ou {@code null} si {@code montantSortie}
     *          est nul
     */
    Long emettreEcritureSortieBS(BonSortie bs, BigDecimal montantSortie);
}

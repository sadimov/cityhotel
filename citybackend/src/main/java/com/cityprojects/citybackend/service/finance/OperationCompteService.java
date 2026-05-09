package com.cityprojects.citybackend.service.finance;

import com.cityprojects.citybackend.entity.finance.OperationCompte;

import java.math.BigDecimal;

/**
 * Service du journal des operations sur compte AUXILIAIRE CLIENT (Tour 22.1).
 *
 * <p><b>⚠️ DÉPRÉCATION SÉMANTIQUE - Tour 20bis.</b> Les entites
 * {@link com.cityprojects.citybackend.entity.finance.Compte} et
 * {@link OperationCompte} representent un audit trail
 * <em>auxiliaire client</em>, <b>PAS</b> une partie double SYSCOHADA. La
 * comptabilite generale (classes 1-9, balances, journaux, FEC) est externalisee
 * vers Dolibarr (bridge a venir tour ulterieur).</p>
 *
 * <p>Ce service implemente le verrou pessimiste sur le solde du compte pour
 * eviter les race conditions sous concurrence : 2 emissions de facture
 * simultanees sur le meme client doivent serialiser leur impact sur
 * {@link com.cityprojects.citybackend.entity.finance.Compte#getSoldeActuel()}.</p>
 *
 * <p>Toutes les methodes operent sous {@code @RequireTenant}.</p>
 */
public interface OperationCompteService {

    /**
     * Enregistre un DEBIT (augmentation de la dette client) issu d'une facture.
     *
     * <p>Met a jour {@code Compte.soldeActuel = soldeAvant + montant}
     * atomiquement (lock pessimiste {@code SELECT ... FOR UPDATE}).</p>
     *
     * @param compteId  compte auxiliaire impacte
     * @param montant   montant TTC de la facture (positif, le sens est porte par DEBIT)
     * @param factureId reference de la facture origine
     * @param libelle   libelle audit (ex. "Facture FACT-2026-MR-000123")
     * @return l'OperationCompte cree avec soldeAvant/soldeApres remplis
     * @throws com.cityprojects.citybackend.exception.ResourceNotFoundException
     *         si le compte n'existe pas pour le tenant courant
     */
    OperationCompte recordDebit(Long compteId, BigDecimal montant, Long factureId, String libelle);

    /**
     * Enregistre un CREDIT (reduction de la dette client) issu d'un paiement.
     *
     * <p>Met a jour {@code Compte.soldeActuel = soldeAvant - montant}
     * atomiquement (lock pessimiste).</p>
     *
     * @param compteId   compte auxiliaire impacte
     * @param montant    montant du paiement (positif)
     * @param paiementId reference du paiement origine
     * @param libelle    libelle audit
     */
    OperationCompte recordCredit(Long compteId, BigDecimal montant, Long paiementId, String libelle);
}

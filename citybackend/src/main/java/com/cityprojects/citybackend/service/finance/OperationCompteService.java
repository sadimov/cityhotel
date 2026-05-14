package com.cityprojects.citybackend.service.finance;

import com.cityprojects.citybackend.dto.finance.FolioDto;
import com.cityprojects.citybackend.entity.finance.OperationCompte;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Service du journal des operations sur compte AUXILIAIRE CLIENT.
 *
 * <p>Les entites {@link com.cityprojects.citybackend.entity.finance.Compte} et
 * {@link OperationCompte} maintiennent l'audit trail
 * <em>auxiliaire client</em> (folio, balance par tiers). La comptabilite
 * generale partie double (classes 1-9, journaux, FEC) est tenue nativement
 * par l'application dans les blocs ulterieurs (B2-B5) - B1 pose les
 * referentiels (PCG, mapping evenements/comptes, exercice) sans encore
 * remplacer le grand-livre auxiliaire ci-dessous.</p>
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

    /**
     * Tour 46 — Folio du compte auxiliaire client filtre par plage de dates.
     *
     * <p>Resout / cree le compte client (idempotent via
     * {@link CompteService#findOrCreateForClient(Long)}), recupere toutes les
     * operations triees chronologiquement, calcule :
     * <ul>
     *   <li>{@code soldeOuverture} : somme algebrique des operations
     *       <em>strictement anterieures</em> a {@code dateDebut} a partir des
     *       {@code (DEBIT/CREDIT, montant)} (convention : DEBIT augmente, CREDIT
     *       diminue le solde-dette).</li>
     *   <li>operations filtrees dans [dateDebut, dateFin] (incluses), avec
     *       {@code soldeApres} <em>recalcule</em> par accumulation depuis
     *       {@code soldeOuverture} (cette valeur est specifique au folio,
     *       distincte du {@code soldeApres} stocke en base).</li>
     *   <li>enrichissement avec {@code factureNumero}, {@code ligneLibelle},
     *       {@code paiementNumero} (lookups O(N) sur le filtre).</li>
     *   <li>{@code soldeCloture} = solde final apres la derniere operation
     *       filtree.</li>
     * </ul>
     *
     * @param clientId  identifiant client (verifie tenant via Hibernate {@code @TenantId})
     * @param dateDebut borne inferieure inclusive (peut etre null = pas de borne basse)
     * @param dateFin   borne superieure inclusive (peut etre null = pas de borne haute)
     * @return folio enrichi
     * @throws com.cityprojects.citybackend.exception.ResourceNotFoundException
     *         si le client n'existe pas pour le tenant courant
     */
    FolioDto findFolio(Long clientId, LocalDate dateDebut, LocalDate dateFin);
}

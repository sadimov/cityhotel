package com.cityprojects.citybackend.service.finance;

import com.cityprojects.citybackend.dto.finance.AffectationCreateDto;
import com.cityprojects.citybackend.dto.finance.PaiementCreateDto;
import com.cityprojects.citybackend.dto.finance.PaiementDto;
import com.cityprojects.citybackend.dto.finance.PaiementGlobalRequest;
import com.cityprojects.citybackend.dto.finance.PaiementLignesRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Service metier des paiements.
 */
public interface PaiementService {

    /**
     * Cree un paiement et l'affecte eventuellement a une facture si
     * {@code factureId} est fourni dans le DTO.
     *
     * <p>Validations :
     * <ul>
     *   <li>{@code montantTotal &gt; 0} (Bean Validation @DecimalMin).</li>
     *   <li>Si {@code factureId} fourni : facture doit exister, statut autorise
     *       (EMISE/PARTIELLEMENT_PAYEE), et {@code montantTotal &lt;= montantRestant}.</li>
     * </ul>
     */
    PaiementDto create(PaiementCreateDto dto);

    PaiementDto findById(Long paiementId);

    Page<PaiementDto> findAll(Pageable pageable);

    /**
     * Affecte un paiement existant a une ou plusieurs factures (ventilation).
     * Recalcule {@code Facture.montantPaye} et la transition de statut.
     */
    PaiementDto affecter(Long paiementId, List<AffectationCreateDto> affectations);

    /**
     * Annule un paiement. Possible uniquement s'il n'a pas d'affectation
     * (sinon il faut d'abord supprimer les affectations).
     */
    PaiementDto annuler(Long paiementId);

    /**
     * Tour 45 : paiement granulaire sur des lignes selectionnees d'une
     * facture. Cree un {@link com.cityprojects.citybackend.entity.finance.Paiement}
     * + 1 {@link com.cityprojects.citybackend.entity.finance.AffectationPaiement}
     * par ligne (avec {@code ligneFactureId}), ventilation
     * <em>proportionnelle</em> au reste de chaque ligne.
     *
     * <p>Excedent (montant payé &gt; somme des restes) : credite sur le compte
     * client via {@code OperationCompteService.recordCredit} avec libelle
     * "Excedent paiement {numeroPaiement}".</p>
     *
     * <p>Recalcule {@code Facture.montantPaye} (somme des affectations) et le
     * statut (PARTIELLEMENT_PAYEE / PAYEE).</p>
     */
    PaiementDto paierLignes(PaiementLignesRequest request);

    /**
     * Tour 46 — Paiement global d'une reservation sans selection de lignes.
     *
     * <p>Le backend ventile automatiquement le montant sur toutes les lignes
     * facture non payees de la reservation, dans l'ordre FIFO (par
     * {@code Facture.dateFacture} ASC puis {@code ligneFactureId} ASC) :
     * <ol>
     *   <li>resolution / creation idempotente du compte client si {@code idCompteClient} null/0,</li>
     *   <li>collecte des lignes facture non payees ({@code montantTtc &gt; montant deja affecte}) via
     *       {@link com.cityprojects.citybackend.repository.finance.LigneFactureRepository#findByReservationId(Long)},</li>
     *   <li>ventilation <em>FIFO sequentielle</em> : chaque ligne recoit son reste complet jusqu'a
     *       epuisement du montant,</li>
     *   <li>si {@code montant &gt; somme des restes} : l'excedent est credite sur le compte client
     *       avec libelle "Avance solde {numeroPaiement}",</li>
     *   <li>si toutes les lignes sont deja payees : tout le montant va en CREDIT compte client
     *       (acompte / avance pure),</li>
     *   <li>recalcul automatique de {@code Facture.montantPaye} + transition de statut via
     *       {@link com.cityprojects.citybackend.entity.finance.AffectationPaiement}.</li>
     * </ol>
     *
     * <p>Differences avec {@link #paierLignes(PaiementLignesRequest)} Tour 45 :
     * <ul>
     *   <li>Selection automatique vs explicite des lignes.</li>
     *   <li>Ventilation FIFO sequentielle vs proportionnelle.</li>
     *   <li>Cas "aucune ligne facturable" supporte : tout en CREDIT (acompte).</li>
     * </ul>
     *
     * @throws com.cityprojects.citybackend.exception.BusinessException
     *         si {@code reservationId} ou {@code idClient} manquent, ou si la reservation
     *         n'a aucune facture (cas non gere : pas de ligne ni d'acompte sans facture).
     */
    PaiementDto payerGlobal(PaiementGlobalRequest request);
}

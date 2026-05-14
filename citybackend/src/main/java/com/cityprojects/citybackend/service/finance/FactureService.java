package com.cityprojects.citybackend.service.finance;

import com.cityprojects.citybackend.dto.finance.FactureCreateDto;
import com.cityprojects.citybackend.dto.finance.FactureDto;
import com.cityprojects.citybackend.dto.finance.LigneFactureDto;
import com.cityprojects.citybackend.dto.finance.LigneFactureRecapDto;
import com.cityprojects.citybackend.dto.finance.LigneServiceCreateRequest;
import com.cityprojects.citybackend.dto.finance.TransfertLignesRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Service metier des factures.
 *
 * <p>Toutes les methodes operent sous {@code @RequireTenant} (TenantContext
 * obligatoire). Elles peuvent lever {@link com.cityprojects.citybackend.exception.BusinessException}
 * (cle i18n) ou {@link com.cityprojects.citybackend.exception.ResourceNotFoundException}.</p>
 */
public interface FactureService {

    /** Cree une facture (et optionnellement ses lignes) en statut BROUILLON. */
    FactureDto create(FactureCreateDto dto);

    FactureDto findById(Long factureId);

    Page<FactureDto> findAll(Pageable pageable);

    /**
     * Transition BROUILLON -&gt; EMISE. La facture devient figee (modifications
     * interdites). Genere une operation comptable DEBIT sur le compte si
     * compteId est renseigne.
     */
    FactureDto emettre(Long factureId);

    /**
     * Annulation d'une facture. Possible uniquement si {@code montantPaye = 0}
     * et statut != PAYEE/PARTIELLEMENT_PAYEE/ANNULEE.
     */
    FactureDto annuler(Long factureId);

    /**
     * Cree une facture a partir d'une reservation : 1 ligne par nuitee
     * CONSOMMEE non encore facturee. Met a jour {@code Reservation.factureId},
     * {@code Nuitee.factureId} et {@code Nuitee.ligneFactureId}, transitionne
     * les nuitees CONSOMMEE -&gt; FACTUREE.
     *
     * <p>Idempotence : si la reservation a deja un {@code factureId},
     * leve {@link com.cityprojects.citybackend.exception.BusinessException}
     * ({@code error.facture.reservation.dejaFacturee}).</p>
     */
    FactureDto fromReservation(Long reservationId);

    /**
     * Cree la facture <b>previsionnelle</b> d'une reservation au moment de sa
     * creation (Tour 44 Phase 1) : 1 ligne par nuitee de la reservation, quel
     * que soit son statut ({@code PREVUE} inclus). La facture passe directement
     * en {@code EMISE}, le DEBIT compte client est passe immediatement.
     *
     * <p>Difference avec {@link #fromReservation(Long)} : cette methode genere
     * la facture <em>avant</em> consommation reelle (la spec calendrier exige
     * que toute reservation cree immediatement sa facture pour suivre les
     * paiements). Les nuitees <b>ne sont pas transitionnees vers FACTUREE</b>
     * (elles le seront a la consommation par le night audit / check-in /
     * check-out). Les champs {@code factureId} et {@code ligneFactureId} sont
     * en revanche renseignes pour traçabilite.</p>
     *
     * <p>Idempotence : si la reservation a deja un {@code factureId},
     * leve {@link com.cityprojects.citybackend.exception.BusinessException}
     * ({@code error.facture.reservation.dejaFacturee}).</p>
     */
    FactureDto previsionFromReservation(Long reservationId);

    /**
     * Cree une facture a partir d'une commande POS comptant (Tour 24) :
     * 1 ligne {@code COMMANDE} par {@code LigneCommande}, montant figeé
     * (snapshot des prix de la commande). La facture est emise immediatement
     * en statut {@code EMISE} (pas de BROUILLON intermediaire).
     *
     * <p>Pre-conditions :</p>
     * <ul>
     *   <li>la commande existe dans le tenant courant ;</li>
     *   <li>{@code commande.modeReglement = COMPTANT} ;</li>
     *   <li>{@code commande.factureId == null} (idempotence : pas de double
     *       facturation).</li>
     * </ul>
     *
     * <p>L'audit trail comptable auxiliaire (DEBIT sur compte client) est
     * applique uniquement si {@code commande.clientId != null} (cas walk-in
     * anonyme : facture cash sans compte auxiliaire).</p>
     */
    FactureDto fromCommande(Long commandeId);

    /**
     * Tour 45 : transfere des lignes selectionnees d'une facture source vers
     * une facture cible. Recalcule les montants des 2 factures et retourne
     * le DTO enrichi de la facture cible.
     *
     * <p><b>Refus</b> (cle i18n) :</p>
     * <ul>
     *   <li>Au moins une ligne deja affectee a un paiement -&gt;
     *       {@code error.facture.transfert.lignePayee}.</li>
     *   <li>Facture source / cible {@code PAYEE} ou {@code ANNULEE} -&gt;
     *       {@code error.facture.transfert.factureSourceTerminated} /
     *       {@code error.facture.transfert.factureCibleTerminated}.</li>
     * </ul>
     */
    FactureDto transfererLignes(TransfertLignesRequest request);

    /**
     * Tour 45 (fix dette technique) : liste les lignes-facture de toutes les
     * factures rattachees a une reservation, enrichies du {@code montantPaye}
     * (somme des affectations paiements sur la ligne) et du {@code reste}
     * (clamp >= 0).
     *
     * <p>Sert l'endpoint {@code GET /api/finance/factures/lignes-by-reservation/{id}}
     * consomme par la modale "Paiements" du calendrier (front hebergement). Elimine
     * le proxy {@code factureId -> ligneFactureId} qui faussait le paiement
     * granulaire pre-Tour 45.</p>
     *
     * <p>Tenant-safety : la jointure {@code LigneFacture -> Facture} est
     * filtree par {@code Facture @TenantId}, donc seules les lignes du tenant
     * courant sont retournees.</p>
     */
    List<LigneFactureRecapDto> findLignesRecapByReservation(Long reservationId);

    /**
     * Tour 51bis - Bridge ServiceHotelier -&gt; LigneFacture.
     *
     * <p>Ajoute une ligne {@code SERVICE} a une facture existante (resolue
     * soit par {@link LigneServiceCreateRequest#factureId()} soit par
     * {@link LigneServiceCreateRequest#reservationId()}) referencant un
     * {@link com.cityprojects.citybackend.entity.inventory.ServiceHotelier}.
     *
     * <p>Comportement :</p>
     * <ol>
     *   <li>Resout le ServiceHotelier (tenant filter automatique). Refus si
     *       absent ({@code error.serviceHotelier.notFound}) ou inactif
     *       ({@code error.ligneService.serviceInactif}).</li>
     *   <li>Resout la facture cible (FK explicite ou recherche par reservation).
     *       Refus si statut {@code PAYEE} ou {@code ANNULEE}
     *       ({@code error.facture.statut.cloturee}).</li>
     *   <li>Cree la {@code LigneFacture} (type {@code SERVICE}, calcul HT/TVA/TTC
     *       par {@code @PrePersist}).</li>
     *   <li>Recalcule les montants de la facture parente.</li>
     *   <li>Si la facture est deja {@code EMISE} ou {@code PARTIELLEMENT_PAYEE}
     *       et qu'un {@code clientId} est rattache, enregistre un DEBIT
     *       complementaire sur le compte auxiliaire client (engagement
     *       supplementaire).</li>
     * </ol>
     *
     * @return DTO de la ligne creee (montants HT/TVA/TTC remplis).
     * @throws com.cityprojects.citybackend.exception.ResourceNotFoundException
     *         si le service ou la facture cible n'existe pas pour le tenant.
     * @throws com.cityprojects.citybackend.exception.BusinessException
     *         si une regle metier est violee (statut, target, service inactif).
     */
    LigneFactureDto addLigneService(LigneServiceCreateRequest request);
}

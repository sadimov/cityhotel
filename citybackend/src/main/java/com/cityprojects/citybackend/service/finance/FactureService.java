package com.cityprojects.citybackend.service.finance;

import com.cityprojects.citybackend.dto.finance.FactureCreateDto;
import com.cityprojects.citybackend.dto.finance.FactureDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

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
}

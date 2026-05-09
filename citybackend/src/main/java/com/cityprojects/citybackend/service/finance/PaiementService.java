package com.cityprojects.citybackend.service.finance;

import com.cityprojects.citybackend.dto.finance.AffectationCreateDto;
import com.cityprojects.citybackend.dto.finance.PaiementCreateDto;
import com.cityprojects.citybackend.dto.finance.PaiementDto;
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
}

package com.cityprojects.citybackend.dto.finance;

import com.cityprojects.citybackend.entity.finance.ModePaiement;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO de creation d'un paiement.
 *
 * <p>Le {@code numeroPaiement} est genere via NumerotationService. Le paiement
 * peut etre cree avec ou sans facture associee (encaissement avance) ; il sera
 * affecte ulterieurement via {@code PaiementService.affecter()}.</p>
 *
 * <p>Si {@code factureId} est fourni, le service cree directement une
 * {@code AffectationPaiement} pour {@code montantTotal}.</p>
 */
public record PaiementCreateDto(
        Long compteId,
        Long factureId,
        @NotNull @DecimalMin(value = "0.01") BigDecimal montantTotal,
        @Size(max = 3) String devise,
        @NotNull ModePaiement modePaiement,
        @Size(max = 100) String referencePaiement,
        LocalDate datePaiement,
        @Size(max = 2000) String commentaires) {
}

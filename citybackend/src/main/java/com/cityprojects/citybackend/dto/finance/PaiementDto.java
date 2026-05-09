package com.cityprojects.citybackend.dto.finance;

import com.cityprojects.citybackend.entity.finance.ModePaiement;
import com.cityprojects.citybackend.entity.finance.StatutPaiement;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * DTO de sortie pour {@link com.cityprojects.citybackend.entity.finance.Paiement}.
 */
public record PaiementDto(
        Long paiementId,
        String numeroPaiement,
        Long compteId,
        BigDecimal montantTotal,
        String devise,
        ModePaiement modePaiement,
        String referencePaiement,
        LocalDate datePaiement,
        StatutPaiement statut,
        String commentaires,
        Long userId,
        List<AffectationPaiementDto> affectations,
        Instant createdAt,
        Instant updatedAt) {
}

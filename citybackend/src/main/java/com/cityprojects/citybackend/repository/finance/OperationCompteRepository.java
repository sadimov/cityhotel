package com.cityprojects.citybackend.repository.finance;

import com.cityprojects.citybackend.entity.finance.OperationCompte;
import com.cityprojects.citybackend.entity.finance.TypeOperationCompte;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository des operations comptables (audit trail compte). Hibernate filtre
 * via @TenantId.
 */
@Repository
public interface OperationCompteRepository extends JpaRepository<OperationCompte, Long> {

    List<OperationCompte> findByCompteIdOrderByDateOperationDesc(Long compteId);

    /**
     * Recupere les operations liees a une facture (DEBIT a l'emission, CREDIT
     * pour avoir/correction). Tour 45 : utilise par
     * {@code NuiteeService.findProvisoires} pour enrichir le DTO avec
     * l'{@code operationCompteId} a destination du frontend.
     */
    List<OperationCompte> findByFactureIdOrderByDateOperationAsc(Long factureId);

    /**
     * Recupere la premiere operation DEBIT d'un compte liee a une facture
     * donnee (audit trail Tour 22.1 : 1 DEBIT par facture). Tour 45 helper
     * pour le DTO {@code NuiteeModificationDto.operationCompteId}.
     */
    default Optional<OperationCompte> findFirstDebitForFacture(Long factureId) {
        return findByFactureIdOrderByDateOperationAsc(factureId).stream()
                .filter(op -> op.getTypeOperation() == TypeOperationCompte.DEBIT)
                .findFirst();
    }
}

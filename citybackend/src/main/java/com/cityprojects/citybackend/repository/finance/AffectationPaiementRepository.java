package com.cityprojects.citybackend.repository.finance;

import com.cityprojects.citybackend.entity.finance.AffectationPaiement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository du pivot affectations_paiements (pas de tenant filter direct -
 * isolation portee par le paiement parent).
 */
@Repository
public interface AffectationPaiementRepository extends JpaRepository<AffectationPaiement, Long> {

    List<AffectationPaiement> findByPaiementIdOrderByDateAffectationAsc(Long paiementId);

    List<AffectationPaiement> findByFactureIdOrderByDateAffectationAsc(Long factureId);

    void deleteByPaiementId(Long paiementId);
}

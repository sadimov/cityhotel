package com.cityprojects.citybackend.repository.finance;

import com.cityprojects.citybackend.entity.finance.AffectationPaiement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

/**
 * Repository du pivot affectations_paiements.
 *
 * <p>{@code AffectationPaiement} porte {@code @TenantId hotelId} depuis B1
 * (2026-05-08). Toutes les requetes Spring Data (y compris l'agregation
 * {@code sumMontantByLigneFactureId}) sont donc filtrees automatiquement par
 * Hibernate via le resolver de tenant courant.</p>
 */
@Repository
public interface AffectationPaiementRepository extends JpaRepository<AffectationPaiement, Long> {

    List<AffectationPaiement> findByPaiementIdOrderByDateAffectationAsc(Long paiementId);

    List<AffectationPaiement> findByFactureIdOrderByDateAffectationAsc(Long factureId);

    void deleteByPaiementId(Long paiementId);

    /**
     * Tour 45 — Somme des montants affectes a une ligne facture specifique
     * (champ {@code ligne_facture_id} ajoute changeset 034). Retourne 0 si
     * aucune affectation.
     */
    @Query("SELECT COALESCE(SUM(ap.montantAffecte), 0) "
            + "FROM AffectationPaiement ap "
            + "WHERE ap.ligneFactureId = :ligneId")
    BigDecimal sumMontantByLigneFactureId(@Param("ligneId") Long ligneId);
}

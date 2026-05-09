package com.cityprojects.citybackend.repository.finance;

import com.cityprojects.citybackend.entity.finance.Paiement;
import com.cityprojects.citybackend.entity.finance.StatutPaiement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repository des paiements (Hibernate filtre auto via @TenantId).
 */
@Repository
public interface PaiementRepository
        extends JpaRepository<Paiement, Long>, JpaSpecificationExecutor<Paiement> {

    Optional<Paiement> findByNumeroPaiement(String numeroPaiement);

    boolean existsByNumeroPaiement(String numeroPaiement);

    Page<Paiement> findByStatutOrderByDatePaiementDesc(StatutPaiement statut, Pageable pageable);

    Page<Paiement> findByCompteIdOrderByDatePaiementDesc(Long compteId, Pageable pageable);

    /**
     * Tous les paiements d'un jour donne, filtres par statut. Utilise par la
     * cloture de caisse journaliere (Tour 26.1) pour ne retenir que les
     * paiements {@code VALIDE} (les ANNULE/EN_ATTENTE/REFUSE sont exclus du
     * total caisse). Hibernate ajoute auto le filtre tenant.
     */
    List<Paiement> findByDatePaiementAndStatut(LocalDate datePaiement, StatutPaiement statut);
}

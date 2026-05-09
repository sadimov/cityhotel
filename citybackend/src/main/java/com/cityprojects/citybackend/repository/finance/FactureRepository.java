package com.cityprojects.citybackend.repository.finance;

import com.cityprojects.citybackend.entity.finance.Facture;
import com.cityprojects.citybackend.entity.finance.StatutFacture;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repository des factures.
 *
 * <p>Le filtre {@code WHERE hotel_id = ?} est ajoute automatiquement par
 * Hibernate via {@code @TenantId}. Toutes les methodes ci-dessous voient donc
 * uniquement les factures du tenant courant.</p>
 */
@Repository
public interface FactureRepository
        extends JpaRepository<Facture, Long>, JpaSpecificationExecutor<Facture> {

    Optional<Facture> findByNumeroFacture(String numeroFacture);

    boolean existsByNumeroFacture(String numeroFacture);

    /** Page des factures par statut, plus recentes d'abord. */
    Page<Facture> findByStatutOrderByDateFactureDesc(StatutFacture statut, Pageable pageable);

    /** Toutes les factures liees a une reservation donnee. */
    List<Facture> findByReservationId(Long reservationId);

    /** Toutes les factures liees a un client donne, plus recentes d'abord. */
    Page<Facture> findByClientIdOrderByDateFactureDesc(Long clientId, Pageable pageable);

    /** Toutes les factures liees a un compte donne. */
    Page<Facture> findByCompteIdOrderByDateFactureDesc(Long compteId, Pageable pageable);

    /** Factures echues (non payees, dont l'echeance est passee). */
    List<Facture> findByDateEcheanceBeforeAndStatutNot(LocalDate date, StatutFacture statut);
}

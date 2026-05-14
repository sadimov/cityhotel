package com.cityprojects.citybackend.repository.finance;

import com.cityprojects.citybackend.entity.finance.Compte;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository des comptes client/societe (Hibernate filtre auto via @TenantId).
 */
@Repository
public interface CompteRepository
        extends JpaRepository<Compte, Long>, JpaSpecificationExecutor<Compte> {

    Optional<Compte> findByNumeroCompte(String numeroCompte);

    boolean existsByNumeroCompte(String numeroCompte);

    Optional<Compte> findByClientId(Long clientId);

    Optional<Compte> findBySocieteId(Long societeId);

    Page<Compte> findAll(Pageable pageable);

    /**
     * Lit le compte avec un verrou pessimiste (SELECT ... FOR UPDATE) pour
     * serialiser les modifications concurrentes du solde.
     *
     * <p>Utilise par {@link com.cityprojects.citybackend.service.finance.OperationCompteService}
     * dans recordDebit/recordCredit pour eviter les race conditions sur
     * {@link Compte#getSoldeActuel()} sous concurrence.</p>
     *
     * <p>Hibernate ajoute automatiquement {@code WHERE hotel_id = ?} via
     * {@code @TenantId}, donc le verrou est cantonne au tenant courant.</p>
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Compte c where c.compteId = :id")
    Optional<Compte> findByIdForUpdate(@Param("id") Long compteId);
}

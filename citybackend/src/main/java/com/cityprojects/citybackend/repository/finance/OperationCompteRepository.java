package com.cityprojects.citybackend.repository.finance;

import com.cityprojects.citybackend.entity.finance.OperationCompte;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository des operations comptables (audit trail compte). Hibernate filtre
 * via @TenantId.
 */
@Repository
@SuppressWarnings("deprecation")
public interface OperationCompteRepository extends JpaRepository<OperationCompte, Long> {

    List<OperationCompte> findByCompteIdOrderByDateOperationDesc(Long compteId);
}

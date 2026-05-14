package com.cityprojects.citybackend.repository.finance;

import com.cityprojects.citybackend.entity.finance.AuditFinanceLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository de l'audit trail finance (Bloc B3).
 *
 * <p>Filtre tenant automatique via Hibernate @TenantId : toutes les methodes
 * ci-dessous voient uniquement les lignes du tenant courant.</p>
 */
@Repository
public interface AuditFinanceLogRepository extends JpaRepository<AuditFinanceLog, Long> {

    /** Recherche par entite cible (entityType + entityId). */
    List<AuditFinanceLog> findByEntityTypeAndEntityIdOrderByEventAtAsc(String entityType, Long entityId);

    /** Recherche par action exacte (paginee, recents d'abord). */
    Page<AuditFinanceLog> findByActionOrderByEventAtDesc(String action, Pageable pageable);
}

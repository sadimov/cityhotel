package com.cityprojects.citybackend.repository.restaurant;

import com.cityprojects.citybackend.entity.restaurant.Ticket;
import com.cityprojects.citybackend.entity.restaurant.TypeTicket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository des tickets imprimes par le POS (Tour 24).
 *
 * <p>Filtre tenant automatique via Hibernate {@code @TenantId}.</p>
 */
@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {

    /** Tickets emis pour une commande (tous types confondus), plus recents d'abord. */
    List<Ticket> findByCommandeIdOrderByDateImpressionDesc(Long commandeId);

    /** Tickets d'un type donne pour une commande (ordre chronologique inverse). */
    List<Ticket> findByCommandeIdAndTypeTicketOrderByDateImpressionDesc(
            Long commandeId, TypeTicket typeTicket);
}

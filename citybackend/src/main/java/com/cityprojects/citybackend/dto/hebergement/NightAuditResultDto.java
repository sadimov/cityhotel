package com.cityprojects.citybackend.dto.hebergement;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Resultat d'une execution du night audit pour un hotel.
 *
 * <p>Tour 13 - retourne par {@code NightAuditService.run()} et par
 * {@code POST /api/hebergement/night-audit/run}.</p>
 *
 * <p><b>Note multi-tenant</b> : {@code hotelId} est inclus a titre informatif
 * dans la reponse (le client interroge sur SON hotel via le tenant courant) ;
 * il n'est PAS lu d'un payload entrant - {@code TenantContext.get()} fait foi.</p>
 *
 * @param hotelId hotel sur lequel le night audit a ete execute
 * @param dateExecution date hoteliere fermee (= today selon le {@link java.time.Clock})
 * @param nbReservationsMarkedNoShow nombre de reservations CONFIRMEE -&gt; NO_SHOW
 * @param nbNuiteesManquantesGenerees nombre de nuitees creees pour combler les trous
 * @param executedAt timestamp UTC de l'execution
 */
public record NightAuditResultDto(
        Long hotelId,
        LocalDate dateExecution,
        int nbReservationsMarkedNoShow,
        int nbNuiteesManquantesGenerees,
        Instant executedAt) {
}

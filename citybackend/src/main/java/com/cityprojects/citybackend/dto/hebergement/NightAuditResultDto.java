package com.cityprojects.citybackend.dto.hebergement;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Resultat d'une execution du night audit pour un hotel.
 *
 * <p>Retourne par {@code NightAuditService.run()} et par
 * {@code POST /api/hebergement/night-audit/run}.</p>
 *
 * <p><b>Note multi-tenant</b> : {@code hotelId} est inclus a titre informatif
 * dans la reponse (le client interroge sur SON hotel via le tenant courant) ;
 * il n'est PAS lu d'un payload entrant - {@code TenantContext.get()} fait foi.</p>
 *
 * @param hotelId                       hotel sur lequel le night audit a ete execute
 * @param dateExecution                 date hoteliere fermee (= currentDate selon
 *                                       {@code HotelDayService}, pas LocalDate.now())
 * @param nbReservationsMarkedNoShow    nombre de reservations CONFIRMEE -&gt; NO_SHOW
 *                                       (dateArrivee &lt;= dateHotel, statut non
 *                                       passe en ARRIVEE)
 * @param nbNuiteesManquantesGenerees   nombre de nuitees creees pour combler les trous
 * @param nbCheckOutAuto                nombre de departs du jour passes en PARTIE via
 *                                       checkOut standard (facture totalement payee
 *                                       OU dette residuelle B2C laissee sur le compte
 *                                       client auxiliaire)
 * @param nbCheckOutExpressAuto         nombre de departs du jour passes en PARTIE via
 *                                       checkOutExpress B2B (resa avec societeId +
 *                                       facture impayee : transfert client-&gt;societe)
 * @param nbErreurs                     nombre d'erreurs rencontrees sur les operations
 *                                       de check-out auto (loggees, n'interrompent pas
 *                                       le run global)
 * @param executedAt                    timestamp UTC de l'execution
 */
public record NightAuditResultDto(
        Long hotelId,
        LocalDate dateExecution,
        int nbReservationsMarkedNoShow,
        int nbNuiteesManquantesGenerees,
        int nbCheckOutAuto,
        int nbCheckOutExpressAuto,
        int nbErreurs,
        Instant executedAt) {
}

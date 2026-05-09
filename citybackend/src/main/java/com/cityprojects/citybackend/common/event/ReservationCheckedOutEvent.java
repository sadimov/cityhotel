package com.cityprojects.citybackend.common.event;

import java.time.LocalDate;
import java.util.List;

/**
 * Event applicatif Spring publie apres COMMIT d'un check-out de reservation
 * (Tour 30 - couplage event-driven cross-modules).
 *
 * <p>Consomme par {@link com.cityprojects.citybackend.service.menage.MenagePlanningEventListener}
 * pour generer une tache de nettoyage QUOTIDIEN par chambre liberee
 * (Workflow A).</p>
 *
 * <h3>Cycle de vie</h3>
 * <p>Publie depuis
 * {@link com.cityprojects.citybackend.service.hebergement.ReservationServiceImpl#checkOut(Long)}
 * via {@link org.springframework.context.ApplicationEventPublisher#publishEvent(Object)}
 * apres {@code save(reservation)} ET la boucle de transition des chambres en
 * NETTOYAGE. Les listeners utilisent {@code @TransactionalEventListener(AFTER_COMMIT)}
 * + {@code @Transactional(REQUIRES_NEW)} pour ne s'executer que si la TX
 * d'origine commit (resilience rollback).</p>
 *
 * <h3>Multi-tenant</h3>
 * <p>{@code hotelId} est embarque dans l'event (lu depuis
 * {@code TenantContext.get()} au moment du publish) car le ThreadLocal du
 * publisher peut avoir ete cleared par le {@code JwtAuthenticationFilter#finally}
 * au moment ou le listener s'execute (callback AFTER_COMMIT). Le listener
 * positionne ensuite le {@code TenantContext} explicitement.</p>
 *
 * @param reservationId identifiant de la reservation venant d'etre check-out
 * @param hotelId       identifiant du tenant (snapshoot au moment du publish)
 * @param dateCheckOut  date du check-out (typiquement LocalDate.now() cote service)
 * @param chambreIds    liste des chambres liberees (1 par {@code ReservationChambre})
 */
public record ReservationCheckedOutEvent(
        Long reservationId,
        Long hotelId,
        LocalDate dateCheckOut,
        List<Long> chambreIds) {
}

package com.cityprojects.citybackend.common.audit;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.entity.finance.AuditFinanceLog;
import com.cityprojects.citybackend.repository.finance.AuditFinanceLogRepository;
import com.cityprojects.citybackend.security.UserPrincipal;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * Aspect AOP qui materialise l'audit log {@code finance.audit_finance_log}
 * pour toute methode annotee {@link AuditFinanceAction}.
 *
 * <h3>Strategie</h3>
 * <p>{@code @Around} : execute la methode, puis (apres succes) tente de
 * resoudre l'entityId :
 * <ol>
 *   <li>premier argument {@code Long} s'il existe ;</li>
 *   <li>sinon premier id trouve sur le DTO retourne (reflection sur les
 *       getters / accesseurs record : {@code id()}, {@code factureId()},
 *       {@code paiementId()}, {@code bonCommandeId()}, {@code bonSortieId()}).</li>
 * </ol>
 *
 * <h3>Ordre AOP</h3>
 * <p>{@link Order} a {@code LOWEST_PRECEDENCE - 5} : meme niveau que
 * {@link AuditActionAspect} pour rester a l'interieur de la transaction
 * Spring. L'INSERT audit partage donc la meme TX que la mutation metier.</p>
 *
 * <h3>Robustesse</h3>
 * <p>Aucune exception levee par l'audit n'est laissee remonter : seul un
 * WARN est logge. Le contrat metier l'audit n'est PAS bloquant pour la
 * compta (contrairement au menage ou il fait partie integrante du flux).</p>
 */
@Aspect
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 5)
public class AuditFinanceActionAspect {

    private static final Logger logger = LoggerFactory.getLogger(AuditFinanceActionAspect.class);

    private final AuditFinanceLogRepository repository;

    public AuditFinanceActionAspect(AuditFinanceLogRepository repository) {
        this.repository = repository;
    }

    @Around("@annotation(com.cityprojects.citybackend.common.audit.AuditFinanceAction)")
    public Object audit(ProceedingJoinPoint pjp) throws Throwable {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method method = signature.getMethod();
        AuditFinanceAction annotation = method.getAnnotation(AuditFinanceAction.class);

        // Capture le premier Long argument AVANT l'execution (cas
        // annuler(Long id), contrePasser(Long id, ...) - l'argument est
        // l'entityId, le DTO retourne peut etre absent).
        Long preEntityId = firstLongArg(pjp);

        Object result = pjp.proceed();

        Long entityId = preEntityId;
        if (entityId == null && result != null) {
            entityId = extractIdFromDto(result);
        }

        try {
            // Ne pose une ligne audit que si on a un tenant courant : un appel
            // depuis un scheduler / boot sans TenantContext (cas hors scope B3)
            // serait sinon rejete par @TenantId. On log un DEBUG et on sort.
            if (TenantContext.getOrNull() == null) {
                logger.debug("AuditFinance: pas de tenant courant pour action={} - skip",
                        annotation.value());
                return result;
            }

            AuditFinanceLog log = new AuditFinanceLog();
            log.setAction(annotation.value());
            log.setEntityType(annotation.entityType());
            log.setEntityId(entityId);
            log.setUserId(currentUserId());
            repository.save(log);
        } catch (RuntimeException ex) {
            // L'audit ne doit pas masquer le succes metier. On WARN seulement.
            logger.warn("AuditFinance INSERT echec pour action={} entityType={} entityId={} : {}",
                    annotation.value(), annotation.entityType(), entityId, ex.getMessage());
        }

        return result;
    }

    /** Extrait le premier argument de type Long s'il existe. */
    private Long firstLongArg(ProceedingJoinPoint pjp) {
        Object[] args = pjp.getArgs();
        for (Object a : args) {
            if (a instanceof Long l) {
                return l;
            }
        }
        return null;
    }

    /**
     * Cherche un id sur le DTO retourne via reflection. Couvre les noms les
     * plus courants (records Java + classes legacy) : {@code id()},
     * {@code factureId()}, {@code paiementId()}, {@code bonCommandeId()},
     * {@code bonSortieId()}, {@code exerciceId()}.
     */
    private Long extractIdFromDto(Object dto) {
        for (String name : new String[]{
                "id", "factureId", "paiementId", "bonCommandeId",
                "bonSortieId", "exerciceId", "ecritureId"}) {
            try {
                Method m = dto.getClass().getMethod(name);
                Object value = m.invoke(dto);
                if (value instanceof Long l) {
                    return l;
                }
            } catch (NoSuchMethodException ignored) {
                // method does not exist on this DTO - try next
            } catch (Exception ex) {
                logger.debug("AuditFinance: reflection echec sur {}#{} : {}",
                        dto.getClass().getSimpleName(), name, ex.getMessage());
            }
        }
        return null;
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof UserPrincipal up) {
            return up.getUserId();
        }
        return null;
    }
}

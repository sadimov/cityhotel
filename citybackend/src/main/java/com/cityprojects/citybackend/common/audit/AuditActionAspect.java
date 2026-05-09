package com.cityprojects.citybackend.common.audit;

import com.cityprojects.citybackend.dto.menage.TacheDto;
import com.cityprojects.citybackend.entity.menage.Tache;
import com.cityprojects.citybackend.repository.menage.TacheRepository;
import com.cityprojects.citybackend.security.UserPrincipal;
import com.cityprojects.citybackend.service.menage.HistoriqueService;
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
 * Aspect AOP qui materialise l'audit log {@code menage.historique} pour toute
 * methode annotee {@link AuditAction}.
 *
 * <p>Tour 30 etape 5 : centralise l'audit (auparavant duplique dans 6 sites de
 * {@code TacheServiceImpl}) et corrige le bug du {@code userId = null}
 * systematique en lisant le {@link UserPrincipal} depuis le SecurityContext.</p>
 *
 * <h3>Strategie {@code @Around}</h3>
 * <ul>
 *   <li>Avant l'execution : si {@code transition=true} ou si l'action est
 *       {@code "suppression"}, on recupere la {@link Tache} pour capter
 *       l'ancien statut (et les FK chambreId/personnelId qui n'existeraient
 *       plus apres DELETE).</li>
 *   <li>Execute la methode.</li>
 *   <li>Apres succes : enregistre l'historique avec le DTO retourne (si
 *       applicable) ou les ids captes avant.</li>
 * </ul>
 *
 * <h3>Ordre AOP</h3>
 * <p>{@link Order} a {@code LOWEST_PRECEDENCE - 5} pour s'executer apres
 * l'aspect transactionnel Spring (a {@code LOWEST_PRECEDENCE}) — autrement dit
 * <i>a l'interieur</i> de la transaction. L'INSERT historique partage donc la
 * meme TX que la mutation metier : si l'INSERT echoue, la mutation rollback ;
 * si la mutation rollback (BusinessException), l'aspect ne s'execute pas
 * (proceed re-throw).</p>
 *
 * <p>Pourquoi {@code -5} et pas {@code -1} : laisse une marge pour intercaler
 * d'autres aspects metier ulterieurs (ex. cache invalidation).</p>
 */
@Aspect
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 5)
public class AuditActionAspect {

    private static final Logger logger = LoggerFactory.getLogger(AuditActionAspect.class);

    private final HistoriqueService historiqueService;
    private final TacheRepository tacheRepository;

    public AuditActionAspect(HistoriqueService historiqueService,
                             TacheRepository tacheRepository) {
        this.historiqueService = historiqueService;
        this.tacheRepository = tacheRepository;
    }

    @Around("@annotation(com.cityprojects.citybackend.common.audit.AuditAction)")
    public Object audit(ProceedingJoinPoint pjp) throws Throwable {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method method = signature.getMethod();
        AuditAction annotation = method.getAnnotation(AuditAction.class);

        // Capture pre-execution : pour la suppression on aura besoin des FK
        // (chambreId, personnelId) qui disparaissent apres DELETE. Pour les
        // transitions on capture l'ancien statut.
        Long preTacheId = extractTacheId(pjp);
        Tache preState = (preTacheId != null) ? tacheRepository.findById(preTacheId).orElse(null) : null;
        String ancienStatut = (preState != null && preState.getStatut() != null)
                ? preState.getStatut().name() : null;
        Long preChambreId = (preState != null) ? preState.getChambreId() : null;
        Long prePersonnelId = (preState != null) ? preState.getPersonnelId() : null;

        Object result = pjp.proceed();

        // Resolution des champs a auditer en privilegiant le DTO retourne
        // (donnees post-execution) puis le snapshot pre-execution en fallback.
        Long tacheId = preTacheId;
        Long chambreId = preChambreId;
        Long personnelId = prePersonnelId;
        String nouveauStatut = null;

        if (result instanceof TacheDto dto) {
            tacheId = dto.tacheId();
            chambreId = dto.chambreId();
            personnelId = dto.personnelId();
            nouveauStatut = dto.statut() != null ? dto.statut().name() : null;
        }

        if (!annotation.transition()) {
            // Pour les actions non-transitionnelles, on ne propage pas le
            // statut courant comme "nouveau" si on n'a pas d'ancien (sinon
            // bruit dans le log : "ancien=null nouveau=PLANIFIEE" pour une
            // simple modification).
            if (ancienStatut == null) {
                nouveauStatut = null;
            }
        }

        String commentaire = annotation.commentaire().isEmpty()
                ? defaultCommentaire(annotation.value())
                : annotation.commentaire();

        Long userId = currentUserId();

        try {
            historiqueService.enregistrer(tacheId, chambreId, personnelId,
                    annotation.value(), ancienStatut, nouveauStatut,
                    commentaire, userId);
        } catch (RuntimeException ex) {
            // L'audit ne doit pas masquer l'echec metier mais on logge pour
            // detecter une regression de l'aspect (ex. FK chambreId NULL alors
            // que la tache existait). On re-throw pour rollback la TX (audit =
            // partie integrante du contrat metier).
            logger.error("Echec INSERT historique pour action='{}' tacheId={} : rollback de la TX",
                    annotation.value(), tacheId, ex);
            throw ex;
        }

        return result;
    }

    /**
     * Extrait le {@code tacheId} du premier parametre {@code Long} de la
     * methode appelee. Toutes les methodes auditees du module menage suivent
     * la convention "premier argument = tacheId" (cf. signatures de
     * {@code TacheService}). On ne lit pas par nom pour rester robuste aux
     * compilations sans {@code -parameters}.
     */
    private Long extractTacheId(ProceedingJoinPoint pjp) {
        Object[] args = pjp.getArgs();
        if (args.length > 0 && args[0] instanceof Long id) {
            return id;
        }
        return null;
    }

    /**
     * Recupere l'id de l'utilisateur depuis le {@link UserPrincipal} pose par
     * {@link com.cityprojects.citybackend.security.JwtAuthenticationFilter}.
     *
     * <p>Retourne {@code null} si :</p>
     * <ul>
     *   <li>aucun {@code SecurityContext} actif (ex. boot, scheduler) ;</li>
     *   <li>le principal n'est pas un {@link UserPrincipal} (ex. test sans
     *       auth, JWT cas ROOT super-admin sans charge utilisateur) ;</li>
     *   <li>l'utilisateur est anonyme (token Spring "anonymousUser").</li>
     * </ul>
     */
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

    /** Compose un commentaire par defaut a partir du libelle d'action. */
    private String defaultCommentaire(String action) {
        return switch (action) {
            case "creation" -> "Tache creee";
            case "modification" -> "Tache modifiee";
            case "assignation" -> "Tache assignee";
            case "debut" -> "Debut de la tache";
            case "fin" -> "Tache terminee";
            case "suppression" -> "Tache supprimee";
            case "annulation" -> "Tache annulee";
            default -> action;
        };
    }
}

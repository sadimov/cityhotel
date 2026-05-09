package com.cityprojects.citybackend.controller.hebergement;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.dto.hebergement.NightAuditResultDto;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.security.UserPrincipal;
import com.cityprojects.citybackend.service.hebergement.NightAuditNotificationService;
import com.cityprojects.citybackend.service.hebergement.NightAuditService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * REST API du night audit (Tour 13).
 *
 * <h3>Endpoints</h3>
 * <ul>
 *   <li>{@code POST /api/hebergement/night-audit/run} : declenche manuellement
 *       le night audit (SUPERADMIN, ADMIN, GERANT, NIGHTAUDIT).</li>
 *   <li>{@code GET  /api/hebergement/night-audit/notifications} : flux SSE des
 *       notifications night audit du tenant courant (tout utilisateur authentifie).</li>
 * </ul>
 *
 * <p>Tour 14 audit B1 : prefixe migre de {@code /api/night-audit} vers
 * {@code /api/hebergement/night-audit}.</p>
 *
 * <p>Aucun {@code hotelId} dans les payloads (CLAUDE.md racine §10) : le tenant
 * est extrait de {@link TenantContext} (positionne par le filtre JWT).</p>
 */
@RestController
@RequestMapping("/api/hebergement/night-audit")
public class NightAuditController {

    private final NightAuditService nightAuditService;
    private final NightAuditNotificationService notificationService;

    public NightAuditController(NightAuditService nightAuditService,
                                NightAuditNotificationService notificationService) {
        this.nightAuditService = nightAuditService;
        this.notificationService = notificationService;
    }

    /**
     * Declenchement manuel du night audit pour le tenant courant.
     *
     * <p>Reservee aux roles privilegies. Renvoie un resume des actions
     * effectuees ({@link NightAuditResultDto}).</p>
     */
    @PostMapping("/run")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','NIGHTAUDIT')")
    public ResponseEntity<NightAuditResultDto> run() {
        return ResponseEntity.ok(nightAuditService.run());
    }

    /**
     * Souscription SSE aux notifications night audit du tenant courant.
     *
     * <p>L'emitter reste ouvert jusqu'a deconnexion client / timeout (1h par
     * defaut) / erreur. Tous les utilisateurs authentifies recoivent les
     * alertes 3-min ; seuls SUPERADMIN/ADMIN/NIGHTAUDIT reçoivent la
     * notification de lancement (filtree cote service).</p>
     */
    @GetMapping(value = "/notifications", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("isAuthenticated()")
    public SseEmitter subscribeNotifications() {
        UserPrincipal principal = currentPrincipal();
        Long userId = principal.getUserId();
        Long hotelId = TenantContext.get();
        String roleCode = principal.getRoleCode();
        return notificationService.register(userId, hotelId, roleCode);
    }

    /**
     * Recupere le {@link UserPrincipal} courant. Leve une {@link BusinessException}
     * (cle i18n {@code error.user.unknown}) si le contexte de securite n'est pas
     * porte par un {@link UserPrincipal} (cas anormal en production).
     */
    private UserPrincipal currentPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            return principal;
        }
        throw new BusinessException("error.user.unknown");
    }
}

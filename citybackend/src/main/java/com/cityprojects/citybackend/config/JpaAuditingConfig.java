package com.cityprojects.citybackend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * Configuration de Spring Data JPA Auditing.
 * <p>
 * Active {@link EnableJpaAuditing} en designant le bean {@code auditorProvider}
 * comme fournisseur d'identite de l'auditeur (createdBy / updatedBy).
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
public class JpaAuditingConfig {

    /**
     * Sentinel utilise hors contexte d'authentification (jobs schedules, init,
     * appels internes systeme).
     */
    private static final String SYSTEM_AUDITOR = "system";

    /**
     * Fournit le username actuellement authentifie pour les colonnes d'audit.
     * Retombe sur "system" si aucun {@link Authentication} n'est present
     * ou si l'auth est anonyme.
     */
    @Bean
    public AuditorAware<String> auditorProvider() {
        return () -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()
                    || "anonymousUser".equals(authentication.getPrincipal())) {
                return Optional.of(SYSTEM_AUDITOR);
            }
            String name = authentication.getName();
            return Optional.ofNullable(name).filter(s -> !s.isBlank()).or(() -> Optional.of(SYSTEM_AUDITOR));
        };
    }
}

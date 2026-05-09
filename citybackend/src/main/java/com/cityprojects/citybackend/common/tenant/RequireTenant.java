package com.cityprojects.citybackend.common.tenant;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marque une methode (ou une classe) qui exige un tenant explicite dans le
 * {@link TenantContext}. Si {@link TenantContext#getOrNull()} renvoie
 * {@code null} a l'invocation, {@link RequireTenantAspect} leve une
 * {@link IllegalStateException} portant le message {@code "error.tenant.missing"}.
 *
 * <p><b>A utiliser sur tous les services / controllers d'un module metier</b>
 * (clients, finance, hebergement, restaurant, inventory, menage, reporting).
 * Idealement appliquee au niveau classe pour couvrir l'ensemble des methodes
 * publiques sans risque d'oubli ; rabattue au niveau methode uniquement quand
 * une seule methode l'exige.</p>
 *
 * <p><b>NE PAS</b> utiliser sur les services globaux ou techniques
 * (auth, admin, hotel-management, schedulers, batch, jobs de purge cross-tenant).
 * Ces services s'executent volontairement en mode ROOT (sentinel
 * {@link CityTenantIdentifierResolver#ROOT}) et n'ont pas de tenant explicite.</p>
 *
 * <p>Cette garde est complementaire de la strategie Option A du
 * {@link CityTenantIdentifierResolver} : elle compense le risque de fuite que
 * le bypass implicite de Hibernate en l'absence de contexte introduirait dans
 * du code metier.</p>
 */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireTenant {
}

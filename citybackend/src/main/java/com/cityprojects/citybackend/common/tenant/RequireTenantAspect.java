package com.cityprojects.citybackend.common.tenant;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

/**
 * Aspect qui verifie la presence d'un tenant dans le {@link TenantContext}
 * avant l'execution de toute methode (ou classe) annotee {@link RequireTenant}.
 *
 * <p>Pas de probleme d'ordre AOP a gerer ici : on n'a pas besoin que la
 * transaction Spring soit ouverte avant de checker un ThreadLocal. Les
 * pointcuts {@code @annotation} et {@code @within} couvrent respectivement
 * l'application au niveau methode et au niveau classe.</p>
 */
@Aspect
@Component
public class RequireTenantAspect {

    @Before("@annotation(com.cityprojects.citybackend.common.tenant.RequireTenant) "
          + "|| @within(com.cityprojects.citybackend.common.tenant.RequireTenant)")
    public void verifyTenantPresent() {
        if (TenantContext.getOrNull() == null) {
            throw new IllegalStateException(TenantContext.ERROR_TENANT_MISSING);
        }
    }
}

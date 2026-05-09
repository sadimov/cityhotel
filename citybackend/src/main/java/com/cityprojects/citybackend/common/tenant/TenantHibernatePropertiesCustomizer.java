package com.cityprojects.citybackend.common.tenant;

import java.util.Map;
import org.hibernate.cfg.MultiTenancySettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.stereotype.Component;

/**
 * Branche le {@link CityTenantIdentifierResolver} (Spring-managed) sur la
 * SessionFactory Hibernate.
 *
 * <h3>Pourquoi pas de cle {@code hibernate.multiTenancy}</h3>
 * <p>En Hibernate 6, l'enum legacy {@code MultiTenancyStrategy} a ete supprime.
 * Il n'existe plus de propriete pour selectionner explicitement
 * {@code DISCRIMINATOR}. Le mode est <b>auto-detecte</b> :
 * <ul>
 *   <li>si une entite porte un champ {@link org.hibernate.annotations.TenantId},
 *       Hibernate active le mode DISCRIMINATOR;</li>
 *   <li>si un {@code MultiTenantConnectionProvider} est enregistre, il active
 *       le mode SCHEMA / DATABASE.</li>
 * </ul>
 * Le seul cablage requis cote application est donc de fournir un
 * {@link org.hibernate.context.spi.CurrentTenantIdentifierResolver} via la
 * propriete {@link MultiTenancySettings#MULTI_TENANT_IDENTIFIER_RESOLVER}
 * ({@code "hibernate.tenant_identifier_resolver"}).</p>
 *
 * <h3>Spring-managed</h3>
 * <p>On passe une <b>instance</b> et non une classe : Hibernate accepte les
 * deux mais avec une instance le bean reste celui cree par Spring (avec son
 * cycle de vie et ses dependances futures), ce qui n'est pas possible quand
 * Hibernate fait lui-meme {@code newInstance()} a partir d'une classe.</p>
 */
@Component
public class TenantHibernatePropertiesCustomizer implements HibernatePropertiesCustomizer {

    private static final Logger logger =
            LoggerFactory.getLogger(TenantHibernatePropertiesCustomizer.class);

    private final CityTenantIdentifierResolver resolver;

    public TenantHibernatePropertiesCustomizer(CityTenantIdentifierResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public void customize(Map<String, Object> hibernateProperties) {
        hibernateProperties.put(MultiTenancySettings.MULTI_TENANT_IDENTIFIER_RESOLVER, resolver);
        logger.info("Multi-tenancy DISCRIMINATOR enabled, resolver: {}",
                resolver.getClass().getSimpleName());
    }
}

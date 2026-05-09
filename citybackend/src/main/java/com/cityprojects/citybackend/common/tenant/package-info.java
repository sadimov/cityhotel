// TODO(palier-2 ou plus tard) : propagation explicite du TenantContext aux
// contextes asynchrones — TaskDecorator pour @Async, ContextSnapshot pour
// Reactor, scope inheritor pour structured-concurrency Java 21+. Aujourd'hui
// ThreadLocal non herite par design (refus InheritableThreadLocal pour eviter
// les fuites cross-tenant sur un pool partage).

/**
 * Infrastructure multi-tenant : contexte porte-par-thread + multi-tenancy
 * natif Hibernate 6 par {@code DISCRIMINATOR}.
 * <p>
 * Architecture (Tour 3B Alt-NEW-6, 2026-05-05) :
 * <ul>
 *   <li>{@link com.cityprojects.citybackend.common.tenant.TenantContext}
 *       — ThreadLocal qui porte le {@code hotelId} courant (alimente par le
 *       JWT filter pour les requetes HTTP).</li>
 *   <li>{@link com.cityprojects.citybackend.common.tenant.CityTenantIdentifierResolver}
 *       — implementation de {@link org.hibernate.context.spi.CurrentTenantIdentifierResolver}
 *       qui lit {@code TenantContext.getOrNull()} a chaque ouverture de Session
 *       Hibernate.</li>
 *   <li>{@link com.cityprojects.citybackend.common.tenant.TenantHibernatePropertiesCustomizer}
 *       — enregistre le resolver Spring-managed dans la SessionFactory via la
 *       propriete {@code hibernate.tenant_identifier_resolver}.</li>
 *   <li>{@link com.cityprojects.citybackend.common.tenant.TenantAware}
 *       — interface marqueur pour les entites hotel-scopees (lisibilite,
 *       contrats des repos custom). Avec {@code @TenantId}, Hibernate gere
 *       lui-meme la valeur a l'INSERT (resolver) et interdit la modification
 *       de la colonne discriminator.</li>
 * </ul>
 * <p>
 * Application aux nouvelles entites :
 * <pre>{@code
 * @Entity
 * public class MaEntite extends AuditableEntity implements TenantAware {
 *     @Id @GeneratedValue private Long id;
 *
 *     @TenantId
 *     @Column(name = "hotel_id", nullable = false, updatable = false)
 *     private Long hotelId;
 *
 *     // getters/setters...
 * }
 * }</pre>
 * <p>
 * Hibernate 6.6 active automatiquement le mode DISCRIMINATOR des qu'il detecte
 * un champ annote {@code @TenantId} couple a un {@code CurrentTenantIdentifierResolver}
 * enregistre. La cle legacy {@code hibernate.multiTenancy} (ex enum
 * {@code MultiTenancyStrategy}) n'existe plus et est ignoree silencieusement.
 * <p>
 * Comportement quand le resolver retourne {@code null} (super-admin global,
 * scheduler, boot) : voir
 * {@link com.cityprojects.citybackend.common.tenant.CityTenantIdentifierResolver}.
 */
package com.cityprojects.citybackend.common.tenant;

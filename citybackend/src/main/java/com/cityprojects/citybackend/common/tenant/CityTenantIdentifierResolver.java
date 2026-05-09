package com.cityprojects.citybackend.common.tenant;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.stereotype.Component;

/**
 * Resout l'identifiant du tenant Hibernate a partir du
 * {@link TenantContext} (ThreadLocal).
 *
 * <p>Hibernate 6 appelle {@link #resolveCurrentTenantIdentifier()} a chaque
 * ouverture de Session/StatelessSession et a chaque debut de transaction pour
 * recuperer la valeur du discriminator a appliquer en SELECT/INSERT/UPDATE
 * sur les entites annotees {@link org.hibernate.annotations.TenantId}.</p>
 *
 * <h3>Strategie : Option A — sentinel ROOT (0L)</h3>
 * <p>Quand le {@link TenantContext} est vide (super-admin global, scheduler,
 * boot Spring, contextes techniques), on retourne {@link #ROOT} = {@code 0L}
 * et on signale Hibernate via {@link #isRoot(Long)}. Hibernate <b>bypass alors
 * tout filtrage de tenant</b> : aucun predicat sur la colonne discriminator
 * n'est ajoute aux SELECT, et l'INSERT s'attend a une valeur explicite
 * (sinon NOT NULL violation).</p>
 *
 * <p><b>Conventions importantes</b> :
 * <ul>
 *   <li>{@code 0L} est strictement RESERVE comme sentinel de root tenant. Il
 *       est <b>interdit</b> comme {@code hotel_id} reel d'un hotel. Les
 *       changesets Liquibase futurs DOIVENT ajouter
 *       {@code CHECK (hotel_id > 0)} sur toute table tenant.</li>
 *   <li>L'usage de cette strategie expose a un risque de fuite inter-tenant si
 *       un service metier oublie de positionner le contexte. La garde
 *       {@code @RequireTenant} (cf. {@link RequireTenant} et
 *       {@link RequireTenantAspect}) verrouille les services / controllers
 *       metiers : toute methode annotee est rejetee si
 *       {@link TenantContext#getOrNull()} est null. Les services globaux
 *       (auth, admin, hotel-management, schedulers) ne sont volontairement
 *       <b>pas</b> annotes : ils utilisent ROOT en pleine connaissance de
 *       cause.</li>
 *   <li>{@link #isRoot(Long)} est <b>null-safe</b> par precaution, meme si
 *       {@link #resolveCurrentTenantIdentifier()} ne retourne jamais null.</li>
 * </ul>
 *
 * <p>Choix vs Option B (resolver retourne null) : Hibernate generait alors un
 * predicat {@code WHERE hotel_id IS NULL} qui rendait le defaut "deny" pour le
 * code metier — mais cassait les services techniques / globaux qui auraient
 * legitimement besoin d'acceder a toutes les donnees (ex. job de purge cross-
 * tenants, super-admin, exports consolides). Option A + garde
 * {@link RequireTenant} couvre les deux besoins : strictement deny en metier,
 * permissif en technique.</p>
 */
@Component
public class CityTenantIdentifierResolver implements CurrentTenantIdentifierResolver<Long> {

    /**
     * Sentinel "root tenant" — utilise quand aucun tenant n'est positionne
     * dans le {@link TenantContext}. INTERDIT comme {@code hotel_id} reel
     * d'un hotel (contraintes {@code CHECK (hotel_id > 0)} a ajouter cote
     * Liquibase).
     */
    public static final Long ROOT = 0L;

    @Override
    public Long resolveCurrentTenantIdentifier() {
        Long tenant = TenantContext.getOrNull();
        return tenant != null ? tenant : ROOT;
    }

    @Override
    public boolean isRoot(Long tenant) {
        // null-safe : ROOT.equals(null) renvoie false sans NPE.
        return ROOT.equals(tenant);
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        // false : on accepte qu'une Session existante n'ait pas le meme tenant
        // que celui resolu maintenant. Spring Boot ouvre typiquement une Session
        // par transaction (pas de session reutilisee entre tx), donc ce cas
        // reste theorique. true forcerait Hibernate a invalider la Session des
        // qu'on change de TenantContext en cours de tx, ce qui n'apporte rien
        // ici et casserait des cas legitimes.
        return false;
    }
}

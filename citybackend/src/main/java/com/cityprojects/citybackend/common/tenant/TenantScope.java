package com.cityprojects.citybackend.common.tenant;

import java.util.function.Supplier;

/**
 * Utilitaire de portee tenant : execute un bloc de code avec un
 * {@link TenantContext} positionne (snapshot/restore propre), garantissant
 * que la valeur precedente est rendue meme en cas d'exception.
 *
 * <h2>Pourquoi cet utilitaire</h2>
 * <p>Les services techniques cross-tenant (admin SUPERADMIN, schedulers,
 * batchs, listeners apres-commit, jobs de reconciliation) ont parfois besoin
 * d'executer une operation pour le compte d'un hotel donne (ex.
 * {@code adminService.createUserForHotel(42L, dto)}). Le pattern naif :</p>
 * <pre>{@code
 * TenantContext.set(42L);
 * try { ... } finally { TenantContext.clear(); }
 * }</pre>
 * <p>est <b>incorrect</b> si l'appelant avait deja un tenant courant : le
 * {@code clear()} ecrase l'etat anterieur. {@link TenantScope} resout ce
 * probleme via un snapshot/restore symetrique :</p>
 * <ul>
 *   <li>capture la valeur actuelle (peut etre {@code null} = mode ROOT) ;</li>
 *   <li>positionne la nouvelle valeur ;</li>
 *   <li>en {@code finally}, restaure exactement l'etat capture (set ou clear).</li>
 * </ul>
 *
 * <h2>Mode ROOT ({@link #runAsRoot})</h2>
 * <p>Pour les operations cross-tenant (liste de tous les hotels, liste de
 * tous les users, requetes globales), {@link #runAsRoot(Supplier)} positionne
 * le thread en mode ROOT (TenantContext vide) le temps du bloc, puis restaure.
 * <b>Tres puissant : a reserver aux services SUPERADMIN.</b> Sous le mode
 * ROOT, le {@link CityTenantIdentifierResolver} retourne le sentinel
 * {@code ROOT = 0L} et Hibernate <b>bypass</b> le filtre {@code @TenantId}
 * (cf. doc resolver). Toutes les requetes verront alors les donnees de tous
 * les tenants — usage strictement reserve a l'administration.</p>
 *
 * <h2>Pas de @RequireTenant possible sur le caller</h2>
 * <p>Les services qui utilisent {@code TenantScope.runAs}/{@code runAsRoot}
 * sont volontairement <b>hors</b> de l'invariant {@code @RequireTenant}
 * (cf. {@link RequireTenant}). Ils s'appuient sur {@code @PreAuthorize}
 * niveau controller pour la securite (typiquement role SUPERADMIN).</p>
 *
 * <h2>Pas de constructeur public</h2>
 * <p>Classe utility pure : tous les membres sont {@code static}, le
 * constructeur prive empeche l'instantiation accidentelle.</p>
 */
public final class TenantScope {

    private TenantScope() {
        // utilitaire pur
    }

    /**
     * Execute {@code action} avec {@code hotelId} positionne dans le
     * {@link TenantContext}, puis restaure l'etat anterieur (peut etre
     * {@code null} = mode ROOT). Renvoie le resultat de l'action.
     *
     * @param hotelId identifiant strictement positif (les contraintes de
     *                {@link TenantContext#set(Long)} s'appliquent : non null,
     *                {@code > 0} ; le 0 est reserve au sentinel ROOT et doit
     *                passer par {@link #runAsRoot(Supplier)}).
     * @param action  bloc a executer (peut lever des exceptions runtime).
     * @param <T>     type de retour
     * @return le resultat de {@code action.get()}
     * @throws IllegalArgumentException si {@code hotelId} est invalide
     *         (cf. {@link TenantContext#set(Long)})
     */
    public static <T> T runAs(Long hotelId, Supplier<T> action) {
        Long previous = TenantContext.getOrNull();
        TenantContext.set(hotelId);
        try {
            return action.get();
        } finally {
            restore(previous);
        }
    }

    /**
     * Variante {@link Runnable} de {@link #runAs(Long, Supplier)} pour les
     * actions sans valeur de retour.
     */
    public static void runAs(Long hotelId, Runnable action) {
        Long previous = TenantContext.getOrNull();
        TenantContext.set(hotelId);
        try {
            action.run();
        } finally {
            restore(previous);
        }
    }

    /**
     * Execute {@code action} en mode ROOT (TenantContext vide) puis restaure
     * l'etat anterieur. Sous ROOT, Hibernate bypass le filtre {@code @TenantId}
     * et toute requete devient cross-tenant.
     *
     * <p><b>RESERVE a l'administration SUPERADMIN.</b> Tout autre usage est
     * un risque de fuite cross-tenant.</p>
     */
    public static <T> T runAsRoot(Supplier<T> action) {
        Long previous = TenantContext.getOrNull();
        TenantContext.clear();
        try {
            return action.get();
        } finally {
            restore(previous);
        }
    }

    /**
     * Restaure l'etat capture : set si non-null, clear sinon. Symetrique au
     * snapshot, jamais d'effet de bord sur l'etat anterieur du caller.
     */
    private static void restore(Long previous) {
        if (previous != null) {
            TenantContext.set(previous);
        } else {
            TenantContext.clear();
        }
    }
}

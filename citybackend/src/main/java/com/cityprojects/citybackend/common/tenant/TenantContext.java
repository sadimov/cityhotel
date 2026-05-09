package com.cityprojects.citybackend.common.tenant;

/**
 * Contexte tenant porte-par-thread.
 * <p>
 * Stocke l'identifiant de l'hotel courant pour la duree d'une requete HTTP
 * (ou d'une tache Spring planifiee qui s'en serait explicitement saisie).
 * <p>
 * Pourquoi {@link ThreadLocal} et pas {@link InheritableThreadLocal} :
 * les sous-traitements asynchrones (@Async, executors, virtual threads) ont
 * leur propre cycle de vie. Heriter automatiquement du tenant pourrait laisser
 * fuiter le contexte sur un pool partage. La propagation explicite (capture
 * dans un Runnable) est preferable et sera traitee dans un tour ulterieur.
 */
public final class TenantContext {

    /**
     * Cle i18n stable pour signaler l'absence de tenant cote API. Utilisee par
     * {@link RequireTenantAspect} et toute couche metier qui veut emettre un
     * code d'erreur traduisible vers le front. NE PAS confondre avec les
     * messages techniques (IllegalArgumentException) destines aux logs et au
     * debug developpeur.
     */
    public static final String ERROR_TENANT_MISSING = "error.tenant.missing";

    private static final ThreadLocal<Long> CURRENT_HOTEL_ID = new ThreadLocal<>();

    private TenantContext() {
        // utilitaire pur
    }

    /**
     * Positionne l'identifiant d'hotel pour le thread courant.
     * <p>
     * Refuse {@code null} (contrat tenant) et toute valeur {@code <= 0} :
     * la valeur 0 est reservee au sentinel ROOT (super-admin / scheduler) et
     * doit etre exprimee par {@link #clear()} (TenantContext vide), pas par
     * {@code set(0L)}.
     *
     * @param hotelId identifiant strictement positif
     * @throws IllegalArgumentException si {@code hotelId} est {@code null} ou {@code <= 0}
     */
    public static void set(Long hotelId) {
        if (hotelId == null) {
            // Message TECHNIQUE (logs / debug) : pas une cle i18n. Ce contrat
            // protege le code metier d'un appel mal cable, ce n'est pas une
            // erreur fonctionnelle remontable a l'utilisateur final.
            throw new IllegalArgumentException("hotelId must not be null");
        }
        if (hotelId <= 0L) {
            throw new IllegalArgumentException(
                    "hotelId must be positive (0 reserved for ROOT sentinel — use TenantContext.clear())");
        }
        CURRENT_HOTEL_ID.set(hotelId);
    }

    /**
     * Retourne l'hotel courant ou leve une exception si absent.
     *
     * @return identifiant d'hotel, jamais null
     * @throws IllegalStateException avec message {@link #ERROR_TENANT_MISSING} si absent
     */
    public static Long get() {
        Long hotelId = CURRENT_HOTEL_ID.get();
        if (hotelId == null) {
            throw new IllegalStateException(ERROR_TENANT_MISSING);
        }
        return hotelId;
    }

    /**
     * Retourne l'hotel courant ou {@code null} sans exception.
     * Utile pour les filtres HTTP, les schedulers et tout code qui doit decider
     * lui-meme du comportement quand le tenant n'est pas encore connu.
     */
    public static Long getOrNull() {
        return CURRENT_HOTEL_ID.get();
    }

    /**
     * Nettoie le ThreadLocal. A appeler IMPERATIVEMENT en {@code finally}
     * a la sortie d'une requete pour eviter la fuite de contexte sur un
     * pool de threads (Tomcat reutilise ses workers).
     */
    public static void clear() {
        CURRENT_HOTEL_ID.remove();
    }

    /**
     * Indique si un tenant est actuellement positionne sur le thread courant.
     */
    public static boolean isSet() {
        return CURRENT_HOTEL_ID.get() != null;
    }
}

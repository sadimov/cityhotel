package com.cityprojects.citybackend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Mode de déploiement de l'application (consigne user 2026-05-22).
 *
 * <p>Deux scénarios opérationnels supportés :</p>
 * <ul>
 *   <li><b>LOCAL</b> : l'application est installée sur un serveur dédié à
 *       <i>un seul</i> hôtel client. Cet hôtel utilise une base PostgreSQL
 *       privée, gère son propre back-up, sa propre supervision. Le mode
 *       multi-tenant Hibernate reste actif mais n'a qu'un seul {@code hotelId}
 *       en base — toutes les transactions ont donc le même tenant. Pratique
 *       pour les hôtels qui veulent garder leurs données 100 % sur site.</li>
 *
 *   <li><b>SAAS</b> : l'application est hébergée sur un serveur central City
 *       Hotel partagé entre plusieurs hôtels clients. Chaque hôtel est isolé
 *       par {@code hotelId} (multi-tenancy DISCRIMINATOR Hibernate).
 *       <b>Mode par défaut</b>.</li>
 * </ul>
 *
 * <h3>Impact sur la numérotation (consigne user 2026-05-22)</h3>
 * <p>Dans les <b>deux modes</b>, les séquences de numérotation (factures,
 * réservations, produits, clients, paiements, BC, BS, commandes, écritures)
 * sont strictement <b>scoped par {@code hotel_id}</b> via la table
 * {@code finance.numerotation_sequence} avec UNIQUE
 * {@code (hotel_id, type, exercice, discriminant)} :</p>
 * <ul>
 *   <li><b>Pas de partage</b> entre hôtels — chaque hôtel a son compteur</li>
 *   <li><b>Successives</b> sans sauts — {@code SELECT ... FOR UPDATE} +
 *       transaction REQUIRED garantissent qu'un rollback du caller annule
 *       l'incrémentation</li>
 *   <li><b>Pas d'interférence</b> — Hibernate {@code @TenantId} ajoute
 *       automatiquement {@code WHERE hotel_id = ?} à toutes les requêtes</li>
 * </ul>
 *
 * <p>Le mode est juste informatif pour audit / observabilité — la logique
 * métier reste identique. Il pilote en pratique :</p>
 * <ul>
 *   <li>L'apparition d'écrans cross-hotel SUPERADMIN dans le front (masqués
 *       en LOCAL où il n'y a qu'un seul hôtel)</li>
 *   <li>Les politiques de back-up (LOCAL = site client, SAAS = central)</li>
 *   <li>Le routage des notifications email/SMS</li>
 * </ul>
 *
 * <h3>Configuration</h3>
 * <pre>
 * app:
 *   deployment:
 *     mode: LOCAL          # ou SAAS (défaut)
 *     hotel-code: HOTEL01  # mode LOCAL : code de l'hôtel unique du serveur
 * </pre>
 *
 * <p>Surchargeable via variable d'env {@code APP_DEPLOYMENT_MODE} et
 * {@code APP_DEPLOYMENT_HOTEL_CODE}.</p>
 */
@Component
@ConfigurationProperties(prefix = "app.deployment")
public class DeploymentProperties {

    public enum Mode {
        /** Serveur dédié à un seul hôtel client (déploiement on-premise). */
        LOCAL,
        /** Hébergement central multi-hôtels (déploiement SaaS — défaut). */
        SAAS
    }

    /** Mode de déploiement. Défaut : SAAS. */
    private Mode mode = Mode.SAAS;

    /**
     * Code de l'hôtel unique du serveur en mode LOCAL. Doit correspondre à
     * un {@code Hotel.hotelCode} existant en base. Ignoré en mode SAAS.
     * Utilisé pour les logs / l'observabilité (les requêtes en mode LOCAL
     * peuvent être marquées avec ce code pour faciliter le tri).
     */
    private String hotelCode;

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public String getHotelCode() {
        return hotelCode;
    }

    public void setHotelCode(String hotelCode) {
        this.hotelCode = hotelCode;
    }

    public boolean isLocal() {
        return mode == Mode.LOCAL;
    }

    public boolean isSaas() {
        return mode == Mode.SAAS;
    }
}

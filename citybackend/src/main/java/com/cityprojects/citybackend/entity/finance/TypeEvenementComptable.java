package com.cityprojects.citybackend.entity.finance;

/**
 * Types d'événements comptables déclenchant une écriture (mapping vers le PCG
 * via {@code finance.compte_mapping}).
 *
 * <p>Chaque valeur a un <b>code par défaut</b> ({@link #defaultCompteCode()})
 * pointant vers un compte du Plan Comptable Général mauritanien (SYSCOHADA).
 * Ce défaut est utilisé si aucun mapping personnalisé n'a été défini par
 * l'hôtel : la comptabilité fonctionne ainsi out-of-the-box pour un nouveau
 * tenant, sans étape de configuration préalable.</p>
 *
 * <p>Les mappings personnalisés (table {@code finance.compte_mapping}) ne sont
 * autorisés que vers des comptes de mouvement ({@code utilisable = true}) du
 * PCG. La validation est portée par {@link com.cityprojects.citybackend.service.finance.CompteMappingService}.</p>
 *
 * <h3>Catégories</h3>
 * <ul>
 *   <li>Ventes (706xxx) : nuitées, restauration, bar, services chambre, etc.</li>
 *   <li>Tiers (4xx) : clients, fournisseurs (auxiliaires SYSCOHADA).</li>
 *   <li>Trésorerie (5xx) : caisses physiques, banques, wallets mobile money.</li>
 *   <li>TVA (445x) : collectée, déductible, à décaisser.</li>
 *   <li>Régularisation : réductions accordées.</li>
 *   <li>Achats/Stocks (6xx/3xx) : marchandises.</li>
 * </ul>
 */
public enum TypeEvenementComptable {

    // ============================== Ventes (706xxx) ==============================
    VENTE_NUITEE_HEBERGEMENT("706100"),
    VENTE_RESTAURATION("706200"),
    VENTE_BAR("706300"),
    VENTE_SERVICE_CHAMBRE("706400"),
    VENTE_BLANCHISSERIE("706500"),
    VENTE_AUTRE_SERVICE("706800"),

    // ============================== Tiers - clients (411xxx, 418xxx) ============
    CLIENT_PARTICULIER("411100"),
    CLIENT_SOCIETE("411200"),
    CLIENT_DOUTEUX("418500"),

    // ============================== Tiers - fournisseurs (401xxx) ===============
    FOURNISSEUR_ORDINAIRE("401100"),

    // ============================== Trésorerie (5xxxxx) =========================
    TRESORERIE_ESPECES("531100"),
    TRESORERIE_BANQUE("512100"),
    TRESORERIE_CHEQUE("512100"),
    TRESORERIE_CARTE_BANCAIRE("512100"),
    TRESORERIE_BANKILY("531400"),
    TRESORERIE_MASRIVI("531401"),
    TRESORERIE_SEDAD("531402"),
    TRESORERIE_CLICK("531403"),
    TRESORERIE_AMANETY("531404"),
    TRESORERIE_BFI_CASH("531405"),
    TRESORERIE_MOOV_MONEY("531406"),
    TRESORERIE_GAZAPAY("531407"),
    TRESORERIE_VIREMENT("512100"),

    // ============================== TVA (445xxx) ================================
    TVA_COLLECTEE("445700"),
    TVA_DEDUCTIBLE("445600"),
    TVA_A_DECAISSER("445800"),

    // ============================== Régularisations ============================
    REDUCTION_ACCORDEE("719100"),

    // ============================== Achats / Stocks ============================
    ACHAT_MARCHANDISES("601000"),
    STOCK_MARCHANDISES("311000");

    private final String defaultCompteCode;

    TypeEvenementComptable(String defaultCompteCode) {
        this.defaultCompteCode = defaultCompteCode;
    }

    /**
     * Code comptable par défaut (Plan Comptable Mauritanien SYSCOHADA).
     * Utilisé en fallback quand {@code finance.compte_mapping} ne contient
     * pas de mapping personnalisé pour le tenant courant.
     */
    public String defaultCompteCode() {
        return defaultCompteCode;
    }
}

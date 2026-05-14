package com.cityprojects.citybackend.entity.finance;

/**
 * Nature d'un compte du Plan Comptable Général SYSCOHADA / mauritanien.
 *
 * <ul>
 *   <li>{@code ACTIF} : classes 2-3-5 partie active du bilan (immobilisations, stocks, trésorerie débitrice).</li>
 *   <li>{@code PASSIF} : classes 1-4 partie passive du bilan (capitaux propres, dettes).</li>
 *   <li>{@code CHARGE} : classe 6, comptes de gestion à solder en fin d'exercice.</li>
 *   <li>{@code PRODUIT} : classe 7, comptes de gestion à solder en fin d'exercice.</li>
 *   <li>{@code MIXTE} : compte pouvant être actif ou passif selon le solde
 *       (ex. trésorerie qui peut être créditrice/débitrice, résultat exercice).</li>
 * </ul>
 *
 * <p>Référentiel : plan comptable mauritanien fourni à la racine du projet
 * ({@code plan_comptable_mauritanien.pdf}).</p>
 */
public enum NatureCompte {
    ACTIF,
    PASSIF,
    CHARGE,
    PRODUIT,
    MIXTE
}

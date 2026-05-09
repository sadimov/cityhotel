package com.cityprojects.citybackend.entity.finance;

/**
 * Modes de paiement supportes par la caisse hotel.
 *
 * <p>Source : {@code modes_paiements.txt} a la racine du projet (Tour 19).
 * Liste indicative ; toute valeur ajoutee (nouveau wallet) doit faire l'objet
 * d'un changeset SQL si la colonne {@code paiements.mode_paiement} est ENUM
 * Postgres - ici stockee en VARCHAR(20) donc evolution sans schema change.</p>
 *
 * <h3>Valeurs</h3>
 * <ul>
 *   <li>{@code ESPECES} : Cash, billets/pieces MRU ou devises.</li>
 *   <li>{@code CHEQUE} : Cheque bancaire.</li>
 *   <li>{@code CARTE_BANCAIRE} : Terminal CB (Visa/Mastercard).</li>
 *   <li>{@code BANKILY} : Wallet mobile Bankily (Mauritel).</li>
 *   <li>{@code MASRIVI} : Wallet Masrivi.</li>
 *   <li>{@code SEDAD} : Wallet SEDAD.</li>
 *   <li>{@code CLICK} : Wallet CLICK (BIM).</li>
 *   <li>{@code AMANETY} : Wallet AMANETY.</li>
 *   <li>{@code BFI_CASH} : Wallet BFI Cash.</li>
 *   <li>{@code MOOV_MONEY} : Wallet MoovMoney.</li>
 *   <li>{@code GAZAPAY} : Wallet GazaPay.</li>
 *   <li>{@code VIREMENT} : Virement bancaire classique (B2B / societe).</li>
 * </ul>
 */
public enum ModePaiement {
    ESPECES,
    CHEQUE,
    CARTE_BANCAIRE,
    BANKILY,
    MASRIVI,
    SEDAD,
    CLICK,
    AMANETY,
    BFI_CASH,
    MOOV_MONEY,
    GAZAPAY,
    VIREMENT
}

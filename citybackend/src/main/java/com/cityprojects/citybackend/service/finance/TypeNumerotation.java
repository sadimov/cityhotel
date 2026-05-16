package com.cityprojects.citybackend.service.finance;

/**
 * Familles de numerotation comptable supportees par le {@link NumerotationService}.
 * <p>
 * Chaque valeur produit une sequence independante par hotel et par exercice,
 * stockee dans {@code finance.numerotation_sequence}.
 * <p>
 * Le nom de l'enum est utilise tel quel comme prefixe du numero genere et
 * comme valeur stockee en base (colonne {@code type VARCHAR(10)}, mappage
 * {@code @Enumerated(EnumType.STRING)}). Toute valeur ajoutee doit donc :
 * <ul>
 *   <li>tenir dans 10 caracteres,</li>
 *   <li>etre stable dans le temps (renommage = migration de donnees),</li>
 *   <li>ne pas contenir de caractere autre que [A-Z].</li>
 * </ul>
 */
public enum TypeNumerotation {

    /** Facture. Format : {@code FACT-{exercice}-{codePays}-{6 chiffres}}. */
    FACT,

    /** Paiement encaisse. Format : {@code PAY-{exercice}-{codePays}-{6 chiffres}}. */
    PAY,

    /** Bon de commande fournisseur (module inventory). */
    BC,

    /** Bon de sortie de stock (module inventory). */
    BS,

    /** Avoir / note de credit (annulation partielle ou totale d'une facture). */
    AVOIR,

    /** Devis preparatoire (non encore facture). */
    DEVIS,

    /** Recu de paiement remis au client (preuve de versement). */
    RECU,

    /** Commande POS du restaurant (ticket de vente). */
    COMM,

    /**
     * Numero client (module clients). Format : {@code CLI-{exercice}-{codePays}-{6 chiffres}}.
     * Genere a la creation d'un nouveau client si l'appelant ne fournit pas de numero
     * explicite. Sequence reset chaque 1er janvier comme les autres types.
     * Ajoute au Tour 8 (integration module clients).
     */
    CLI,

    /**
     * Numero reservation (module hebergement). Format :
     * {@code RES-{exercice}-{codePays}-{6 chiffres}}.
     * Genere a la creation d'une reservation. Sequence reset chaque 1er janvier
     * comme les autres types.
     * Ajoute au Tour 11 (integration module hebergement).
     */
    RES,

    /**
     * Code produit (module inventory). Format :
     * {@code PROD-{exercice}-{codePays}-{6 chiffres}}, par exemple
     * {@code PROD-2026-MR-000001}.
     * <p>Genere automatiquement par {@code ProduitServiceImpl.create()} quand
     * le DTO ne fournit pas de {@code codeProduit} explicite. Reset chaque
     * 1er janvier comme les autres types.</p>
     */
    PROD,

    /**
     * Ecriture comptable (journal). Format :
     * {@code JRN-{codeJournal}-{exercice}-{codePays}-{6 chiffres}}, par exemple
     * {@code JRN-VTE-2026-MR-000123}.
     * <p>Particularite : seule famille de numerotation qui exige un
     * <b>discriminant</b> supplementaire (le code du journal comptable :
     * VTE, ACH, BAN, CAI, OD, AVO). La sequence est donc segmentee par
     * (hotel, type=JRN, exercice, discriminant), ce qui impose un appel
     * {@link NumerotationService#next(TypeNumerotation, String)}.</p>
     * Ajoute au Bloc B2 (compta native - partie double).
     */
    JRN
}

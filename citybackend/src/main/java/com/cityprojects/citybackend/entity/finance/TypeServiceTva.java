package com.cityprojects.citybackend.entity.finance;

import java.math.BigDecimal;

/**
 * Types de services hôteliers du point de vue TVA (Bloc B4).
 *
 * <p>Sépare {@link TypeLigneFacture} (mapping comptable côté produit 706xxx)
 * de la grille TVA appliquée. Permet à un hôtel de configurer un taux
 * différent par type de prestation via {@code finance.taux_tva_config}
 * (entité {@link TauxTvaConfig}).</p>
 *
 * <h3>Cadre fiscal mauritanien</h3>
 * <p>Code Général des Impôts mauritanien : taux standard de TVA = 16 %.</p>
 * <ul>
 *   <li>Hébergement (nuitées) : généralement <b>exonéré</b> dans la pratique
 *       hôtelière mauritanienne courante (sauf régime touristique
 *       conventionné). Défaut applicatif : 0 %, surchargable par l'admin.</li>
 *   <li>Restauration / bar / autres prestations hôtelières / achats : 16 %.</li>
 * </ul>
 *
 * <h3>Défauts applicatifs</h3>
 * <p>{@link #defaultTaux()} et {@link #defaultLibelle()} sont la source de
 * vérité de fallback : si l'hôtel n'a aucune ligne dans
 * {@code finance.taux_tva_config} pour ce type, on applique le défaut codé.
 * Le seed à la création d'un hôtel
 * ({@code TauxTvaConfigInitializer}) matérialise ces défauts en base afin
 * de permettre une surcharge ultérieure via l'UI admin.</p>
 */
public enum TypeServiceTva {

    /**
     * Nuitées d'hébergement. Défaut <b>0 %</b> : exonération réglementaire
     * de fait sur la majorité des hôtels mauritaniens (palier 1). L'admin
     * peut surcharger à 16 % si l'établissement est concerné par la TVA
     * hébergement.
     */
    HEBERGEMENT_NUITEE(new BigDecimal("0.00"), "Hebergement nuitee (defaut exonere)"),

    /** Prestations de restauration. Défaut 16 %. */
    RESTAURATION(new BigDecimal("16.00"), "Restauration"),

    /** Consommations au bar. Défaut 16 %. */
    BAR(new BigDecimal("16.00"), "Bar"),

    /** Services en chambre (room service). Défaut 16 %. */
    SERVICE_CHAMBRE(new BigDecimal("16.00"), "Service en chambre"),

    /** Blanchisserie / pressing. Défaut 16 %. */
    BLANCHISSERIE(new BigDecimal("16.00"), "Blanchisserie"),

    /** Autres services hôteliers (spa, navette, location de salle, etc.). Défaut 16 %. */
    AUTRE_SERVICE_HOTELIER(new BigDecimal("16.00"), "Autre service hotelier"),

    /**
     * Achats de marchandises auprès des fournisseurs (TVA déductible côté
     * BonCommande). Défaut 16 %.
     */
    ACHAT_MARCHANDISES(new BigDecimal("16.00"), "Achat marchandises (TVA deductible)");

    private final BigDecimal defaultTaux;
    private final String defaultLibelle;

    TypeServiceTva(BigDecimal defaultTaux, String defaultLibelle) {
        this.defaultTaux = defaultTaux;
        this.defaultLibelle = defaultLibelle;
    }

    /** Taux TVA par défaut, en pourcentage (precision 5, scale 2). */
    public BigDecimal defaultTaux() {
        return defaultTaux;
    }

    /** Libellé descriptif par défaut. */
    public String defaultLibelle() {
        return defaultLibelle;
    }
}

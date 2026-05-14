package com.cityprojects.citybackend.dto.finance.comptabilite;

import java.math.BigDecimal;
import java.util.List;

/**
 * Rubrique du compte de resultat (B5).
 *
 * <p>Exemples :</p>
 * <ul>
 *   <li>PRODUITS : "Ventes hebergement" (7061x), "Ventes restauration" (7062x),
 *       "Ventes bar" (7063x), "Autres ventes" (7064x-7068x), "Reductions
 *       accordees" (719x), "Produits exceptionnels" (754x) ;</li>
 *   <li>CHARGES : "Achats consommes" (601x), "Charges externes" (611x-627x),
 *       "Charges de personnel" (641x), "Charges d'amortissement" (656x),
 *       "Charges financieres" (671x).</li>
 * </ul>
 */
public record RubriqueResultatDto(
        String code,
        String libelle,
        List<LigneResultatDto> lignes,
        BigDecimal montant) {
}

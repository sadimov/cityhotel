package com.cityprojects.citybackend.service.finance;

/**
 * Service central de numerotation comptable.
 * <p>
 * Expose une seule operation : generer le prochain numero pour un type donne,
 * sequentiellement, par hotel et par exercice. Le format produit est
 * {@code <TYPE>-<EXERCICE>-<CODE_PAYS>-<6 chiffres>}, exemples :
 * <pre>
 *   FACT-2026-MR-000123
 *   PAY-2026-FR-000045
 *   BC-2026-MR-000007
 * </pre>
 * Garanties :
 * <ul>
 *   <li><b>Unicite</b> : verrou pessimiste sur la ligne de compteur.</li>
 *   <li><b>Pas de trou</b> : si la transaction appelante rollback apres avoir
 *       obtenu le numero, le compteur revient a son etat anterieur (la
 *       transaction est commune).</li>
 *   <li><b>Isolation tenant</b> : aucune fuite inter-hotel possible (le
 *       compteur est segmente par hotel_id, lui-meme issu du
 *       {@link com.cityprojects.citybackend.common.tenant.TenantContext}).</li>
 * </ul>
 */
public interface NumerotationService {

    /**
     * Genere le prochain numero pour le type donne, sans discriminant
     * additionnel (cas standard FACT, PAY, BC, BS, etc.).
     *
     * @param type famille de numerotation (cf. {@link TypeNumerotation})
     * @return numero formate, jamais {@code null}
     * @throws IllegalStateException si {@link com.cityprojects.citybackend.common.tenant.TenantContext}
     *                               est vide (garde {@code @RequireTenant} en amont)
     *                               ou si l'hotel courant n'existe plus
     */
    String next(TypeNumerotation type);

    /**
     * Genere le prochain numero pour un type donne, avec un discriminant
     * supplementaire qui segmente la sequence.
     *
     * <p>Cas d'usage principal : {@link TypeNumerotation#JRN} (ecriture
     * comptable) ou la sequence doit etre segmentee par code de journal
     * (VTE, ACH, BAN, ...). Le format produit dans ce cas est :</p>
     * <pre>JRN-{discriminant}-{exercice}-{codePays}-{6 chiffres}</pre>
     *
     * <p>Pour les types qui n'utilisent pas de discriminant (FACT, PAY, ...),
     * le format reste {@code TYPE-{exercice}-{codePays}-{6 chiffres}} et le
     * discriminant fourni (s'il l'est) est ignore.</p>
     *
     * @param type         famille de numerotation
     * @param discriminant discriminant additionnel (peut etre {@code null}
     *                     pour les types qui n'en ont pas). Pour JRN, c'est
     *                     le code journal et il est obligatoire.
     * @return numero formate, jamais {@code null}
     * @throws IllegalStateException    si TenantContext est vide ou si l'hotel
     *                                  courant n'existe plus
     * @throws IllegalArgumentException si {@code type == null}, ou si JRN est
     *                                  passe sans discriminant
     */
    String next(TypeNumerotation type, String discriminant);
}

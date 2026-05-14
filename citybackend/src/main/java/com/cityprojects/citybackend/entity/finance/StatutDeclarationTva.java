package com.cityprojects.citybackend.entity.finance;

/**
 * Cycle de vie d'une {@link DeclarationTva} (B4).
 *
 * <pre>
 *   BROUILLON  --valider-->  VALIDEE
 * </pre>
 *
 * <p>Une déclaration {@code VALIDEE} ne peut plus être recalculée ni
 * modifiée ; sa validation génère atomiquement l'écriture comptable de
 * liquidation (DÉBIT 445700 / CRÉDIT 445600 / CRÉDIT 445800).</p>
 */
public enum StatutDeclarationTva {
    BROUILLON,
    VALIDEE
}

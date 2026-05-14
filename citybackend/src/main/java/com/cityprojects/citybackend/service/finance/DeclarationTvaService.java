package com.cityprojects.citybackend.service.finance;

import com.cityprojects.citybackend.dto.finance.DeclarationTvaDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Service de gestion des déclarations TVA (B4).
 *
 * <p>Toutes les opérations sont tenant-scopees ({@code @RequireTenant}).</p>
 */
public interface DeclarationTvaService {

    /**
     * Calcule la déclaration TVA pour une période donnée (typiquement un mois).
     *
     * <p>Lit les écritures comptables VALIDEE dont la {@code dateComptable}
     * appartient à {@code [dateDebut, dateFin]} et somme les lignes sur les
     * comptes 445700 (TVA collectée, sens CREDIT) et 445600 (TVA déductible,
     * sens DEBIT).</p>
     *
     * <p>Idempotence : si une déclaration BROUILLON ou VALIDEE existe déjà
     * pour exactement cette période, elle est renvoyée telle quelle (pas de
     * recalcul, pas de doublon).</p>
     */
    DeclarationTvaDto calculer(LocalDate dateDebut, LocalDate dateFin);

    /**
     * Persiste la validation d'une déclaration. Génère atomiquement
     * l'écriture comptable de liquidation (journal OD) :
     * <pre>
     *   DEBIT  445700 (TVA collectée du mois)
     *   CREDIT 445600 (TVA déductible du mois)
     *   CREDIT 445800 (TVA à décaisser, le solde positif)
     *     ou
     *   DEBIT  445800 (crédit reportable, solde négatif)
     * </pre>
     *
     * <p>Refus métier :</p>
     * <ul>
     *   <li>{@code error.declaration.dejaValidee} si la déclaration est
     *       déjà validée ;</li>
     *   <li>{@code error.declaration.aucunMontant} si collectée + déductible
     *       sont à zéro (pas d'écriture de liquidation à générer).</li>
     * </ul>
     */
    DeclarationTvaDto valider(Long declarationId);

    Page<DeclarationTvaDto> findAll(Pageable pageable);

    DeclarationTvaDto findById(Long id);

    /** Recherche directe par période exacte (pour les contrôleurs front). */
    Optional<DeclarationTvaDto> findByPeriode(LocalDate dateDebut, LocalDate dateFin);
}

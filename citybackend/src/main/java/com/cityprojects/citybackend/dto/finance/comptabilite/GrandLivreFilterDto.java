package com.cityprojects.citybackend.dto.finance.comptabilite;

import java.time.LocalDate;

/**
 * Filtres d'execution du grand livre (B5).
 *
 * <p>{@code compteCode} optionnel : si {@code null} ou blanc, le grand livre
 * porte sur tous les comptes utilises sur la periode.</p>
 *
 * <p>{@code exerciceId} : utilise pour resoudre les bornes par defaut ET pour
 * borner le calcul du report initial (solde au {@code dateDebut - 1 jour} a
 * partir du debut de l'exercice).</p>
 */
public record GrandLivreFilterDto(
        String compteCode,
        Long exerciceId,
        LocalDate dateDebut,
        LocalDate dateFin) {
}

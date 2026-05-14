package com.cityprojects.citybackend.service.finance;

import com.cityprojects.citybackend.dto.finance.ExerciceDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;

/**
 * Service de gestion des exercices comptables (par hôtel).
 *
 * <p>Toutes les opérations sont tenant-scopées ({@code @RequireTenant}).</p>
 */
public interface ExerciceService {

    /**
     * Récupère l'exercice contenant la date du jour pour le tenant courant,
     * ou le crée automatiquement (année calendaire) si aucun exercice ne
     * couvre cette date.
     *
     * <p>Sérialisation par verrou pessimiste pour éviter les doublons sous
     * concurrence (2 services qui détectent l'absence d'exercice
     * simultanément).</p>
     */
    ExerciceDto getOrCreateCurrent();

    /**
     * Vérifie que la date passée appartient à un exercice {@code OUVERT} (ou
     * crée l'exercice courant à la volée si nécessaire). Lève
     * {@code BusinessException("error.exercice.cloture")} si l'exercice
     * couvrant la date est {@code EN_CLOTURE} ou {@code CLOTURE}.
     *
     * <p>Si la date est antérieure à tout exercice existant et hors année
     * courante, lève {@code BusinessException("error.exercice.dateInvalide")}.</p>
     */
    void assertOuvert(LocalDate date);

    ExerciceDto findById(Long id);

    Page<ExerciceDto> findAll(Pageable pageable);

    /**
     * Passe l'exercice de {@code OUVERT} ou {@code EN_CLOTURE} à
     * {@code CLOTURE}. Réservé aux SUPERADMIN/ADMIN (cf. controller).
     *
     * <p>La validation balance équilibrée sera ajoutée au bloc B5. À B1, la
     * clôture est une simple transition de statut + horodatage + auteur.</p>
     */
    ExerciceDto cloturer(Long id);
}

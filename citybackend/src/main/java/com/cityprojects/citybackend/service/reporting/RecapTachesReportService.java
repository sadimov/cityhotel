package com.cityprojects.citybackend.service.reporting;

import com.cityprojects.citybackend.dto.reporting.RecapTacheDto;
import com.cityprojects.citybackend.dto.reporting.RecapTacheDto.TacheGroupBy;

import java.time.LocalDate;

/**
 * Rapport R-MEN-001 — Recap taches menage (Tour 41 P2).
 */
public interface RecapTachesReportService {

    RecapTacheDto computeRecap(LocalDate from, LocalDate to, TacheGroupBy groupBy);

    byte[] exportXlsx(LocalDate from, LocalDate to, TacheGroupBy groupBy);

    /** Helper pour le dashboard direction : nombre de taches en cours (statut EN_COURS). */
    long countTachesEnCours();
}

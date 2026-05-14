package com.cityprojects.citybackend.service.reporting;

import com.cityprojects.citybackend.dto.reporting.EncoursClientDto;

import java.time.LocalDate;

/**
 * Rapport R-FIN-002 — Encours clients (Tour 41 P1).
 */
public interface EncoursClientsReportService {

    /**
     * Calcule l'encours par bucket d'age (0-30, 30-60, 60-90, 90+) a la date
     * de reference {@code reference}.
     *
     * @param reference date de calcul (par defaut {@code LocalDate.now()} si null)
     */
    EncoursClientDto computeEncours(LocalDate reference);

    byte[] exportXlsx(LocalDate reference);
}

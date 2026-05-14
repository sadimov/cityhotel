package com.cityprojects.citybackend.service.finance.comptabilite;

import com.cityprojects.citybackend.dto.finance.comptabilite.JournalEditionDto;
import com.cityprojects.citybackend.dto.finance.comptabilite.JournalFilterDto;

/**
 * Service d'edition d'un journal comptable sur une periode (B5).
 *
 * <p>Liste toutes les ecritures d'un journal (cf. {@code JournalComptable})
 * sur la plage de dates, avec leur detail de lignes et les totaux.</p>
 */
public interface JournalEditionService {

    /** Calcule l'edition de journal selon le filtre. */
    JournalEditionDto compute(JournalFilterDto filter);

    /** Export XLSX (1 feuille - 1 bloc par ecriture). */
    byte[] exportXlsx(JournalFilterDto filter);

    /** Export PDF (portrait). */
    byte[] exportPdf(JournalFilterDto filter);
}

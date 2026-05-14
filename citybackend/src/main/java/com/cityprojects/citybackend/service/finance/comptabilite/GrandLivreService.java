package com.cityprojects.citybackend.service.finance.comptabilite;

import com.cityprojects.citybackend.dto.finance.comptabilite.GrandLivreDto;
import com.cityprojects.citybackend.dto.finance.comptabilite.GrandLivreFilterDto;

/**
 * Service de calcul et d'export du grand livre comptable (B5).
 *
 * <p>Detail des lignes d'ecriture par compte sur une periode, avec report
 * initial (solde au {@code dateDebut - 1 jour}) et solde progressif ligne par
 * ligne.</p>
 */
public interface GrandLivreService {

    /** Calcule le grand livre selon le filtre. */
    GrandLivreDto compute(GrandLivreFilterDto filter);

    /** Export XLSX (1 feuille - 1 bloc par compte). */
    byte[] exportXlsx(GrandLivreFilterDto filter);

    /** Export PDF (paysage). */
    byte[] exportPdf(GrandLivreFilterDto filter);
}

package com.cityprojects.citybackend.service.finance.comptabilite;

import com.cityprojects.citybackend.dto.finance.comptabilite.CompteResultatDto;

import java.time.LocalDate;

/**
 * Service de calcul et d'export du compte de resultat SYSCOHADA simplifie (B5).
 *
 * <p>Periode typique = un exercice (du 1er janvier au 31 decembre).</p>
 */
public interface CompteResultatService {

    /**
     * Calcule le compte de resultat sur la periode. Si {@code dateDebut} ou
     * {@code dateFin} est {@code null}, utilise les bornes de l'exercice.
     */
    CompteResultatDto compute(Long exerciceId, LocalDate dateDebut, LocalDate dateFin);

    byte[] exportXlsx(Long exerciceId, LocalDate dateDebut, LocalDate dateFin);

    byte[] exportPdf(Long exerciceId, LocalDate dateDebut, LocalDate dateFin);
}

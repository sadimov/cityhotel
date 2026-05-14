package com.cityprojects.citybackend.service.reporting;

import com.cityprojects.citybackend.dto.reporting.MouvementValoriseDto;
import com.cityprojects.citybackend.entity.inventory.TypeMouvementStock;

import java.time.LocalDate;

/**
 * Rapport R-INV-002 — Mouvements de stock valorises (Tour 41 P2).
 */
public interface MouvementsValorisesReportService {

    /**
     * Calcule les mouvements valorises sur la plage [from, to).
     *
     * @param from        borne inclusive
     * @param to          borne exclusive
     * @param typeFilter  null = tous, sinon filtre sur le type
     */
    MouvementValoriseDto computeMouvements(LocalDate from, LocalDate to, TypeMouvementStock typeFilter);

    byte[] exportXlsx(LocalDate from, LocalDate to, TypeMouvementStock typeFilter);
}

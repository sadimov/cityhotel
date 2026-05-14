package com.cityprojects.citybackend.dto.reporting;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Rapport R-RES-002 — Top articles vendus sur une periode (Tour 41 P2).
 */
public record TopArticleDto(
        LocalDate from,
        LocalDate to,
        Integer limit,
        List<TopArticleLigneDto> articles
) {

    public record TopArticleLigneDto(
            int rang,
            Long articleId,
            String libelle,
            BigDecimal quantiteVendue,
            BigDecimal caTotal
    ) {
    }
}

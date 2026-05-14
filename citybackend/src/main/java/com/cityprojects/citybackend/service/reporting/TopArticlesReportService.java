package com.cityprojects.citybackend.service.reporting;

import com.cityprojects.citybackend.dto.reporting.TopArticleDto;

import java.time.LocalDate;

/**
 * Rapport R-RES-002 — Top articles vendus (Tour 41 P2).
 */
public interface TopArticlesReportService {

    TopArticleDto findTopArticles(LocalDate from, LocalDate to, int limit);

    byte[] exportXlsx(LocalDate from, LocalDate to, int limit);
}

package com.cityprojects.citybackend.dto.reporting.projection;

import java.math.BigDecimal;

/**
 * Projection JPQL : top articles vendus sur une periode (R-RES-002).
 */
public interface TopArticleProjection {

    Long getArticleId();

    String getLibelle();

    BigDecimal getQuantiteVendue();

    BigDecimal getCaTotal();
}

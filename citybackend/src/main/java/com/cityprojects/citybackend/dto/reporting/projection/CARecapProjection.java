package com.cityprojects.citybackend.dto.reporting.projection;

import java.math.BigDecimal;

/**
 * Projection JPQL : agregats CA sur une periode (R-FIN-001).
 */
public interface CARecapProjection {
    Long getNbFactures();

    BigDecimal getCaEmisHt();

    BigDecimal getCaEmisTva();

    BigDecimal getCaEmisTtc();

    BigDecimal getCaPayeTtc();
}

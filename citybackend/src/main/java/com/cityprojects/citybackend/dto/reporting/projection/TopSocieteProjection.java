package com.cityprojects.citybackend.dto.reporting.projection;

import java.math.BigDecimal;

/**
 * Projection JPQL : agregat par societe sur une periode (R-FIN-004).
 */
public interface TopSocieteProjection {

    Long getSocieteId();

    String getSocieteNom();

    String getSiret();

    Long getNbFactures();

    BigDecimal getCaTtc();

    BigDecimal getCaPaye();
}

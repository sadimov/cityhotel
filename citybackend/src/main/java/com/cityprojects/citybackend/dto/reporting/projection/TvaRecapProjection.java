package com.cityprojects.citybackend.dto.reporting.projection;

import java.math.BigDecimal;

/**
 * Projection JPQL : agregat TVA par dimension (taux ou mois) — R-FIN-003.
 */
public interface TvaRecapProjection {

    /** Cle de groupage (taux %, ou yyyy-MM). */
    String getDimension();

    BigDecimal getTotalHt();

    BigDecimal getTotalTva();

    BigDecimal getTotalTtc();
}

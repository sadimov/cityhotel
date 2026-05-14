package com.cityprojects.citybackend.dto.reporting.projection;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Projection JPQL : ligne de facture enrichie de la {@code dateFacture} parente
 * pour le groupage par mois cote service (R-FIN-003).
 */
public interface LigneFactureMonthProjection {

    LocalDate getDateFacture();

    BigDecimal getMontantHt();

    BigDecimal getMontantTva();

    BigDecimal getMontantTtc();
}

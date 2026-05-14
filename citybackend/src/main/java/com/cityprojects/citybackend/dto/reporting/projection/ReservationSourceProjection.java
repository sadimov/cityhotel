package com.cityprojects.citybackend.dto.reporting.projection;

import java.math.BigDecimal;

/**
 * Projection JPQL : agregat reservations par canal de distribution (R-HEB-004).
 */
public interface ReservationSourceProjection {

    /** Peut etre NULL (legacy) - le service le remplace par "NON_RENSEIGNE". */
    String getSourceCanal();

    Long getNbReservations();

    BigDecimal getCaMontant();
}

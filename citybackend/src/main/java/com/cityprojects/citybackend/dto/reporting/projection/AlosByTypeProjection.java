package com.cityprojects.citybackend.dto.reporting.projection;

/**
 * Projection JPQL : agregat ALOS par type de chambre (R-HEB-002).
 *
 * <p>Le calcul de la moyenne ALOS est fait cote service apres recuperation
 * (cast BigDecimal proprement).</p>
 */
public interface AlosByTypeProjection {

    String getTypeCode();

    String getTypeNom();

    Long getNbReservations();

    Long getTotalNuits();
}

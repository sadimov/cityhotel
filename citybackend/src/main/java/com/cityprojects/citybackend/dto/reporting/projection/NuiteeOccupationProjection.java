package com.cityprojects.citybackend.dto.reporting.projection;

/**
 * Projection JPQL : nombre de nuitees consommees / facturees par type de chambre
 * sur une periode (R-HEB-001).
 */
public interface NuiteeOccupationProjection {
    Long getTypeId();

    Long getNbNuiteesOccupees();
}

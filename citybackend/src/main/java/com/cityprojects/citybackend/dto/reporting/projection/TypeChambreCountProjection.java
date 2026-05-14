package com.cityprojects.citybackend.dto.reporting.projection;

/**
 * Projection JPQL : nombre de chambres actives par type pour le tenant courant (R-HEB-001).
 */
public interface TypeChambreCountProjection {
    Long getTypeId();

    String getTypeCode();

    String getTypeNom();

    Long getNbChambres();
}

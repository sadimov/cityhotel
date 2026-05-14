package com.cityprojects.citybackend.dto.reporting.projection;

/**
 * Projection JPQL : sorties produit sur une periode (R-INV-003 rotation).
 */
public interface RotationProduitProjection {

    Long getProduitId();

    String getCodeProduit();

    String getNomProduit();

    Long getTotalSorties();

    Integer getStockActuel();
}

package com.cityprojects.citybackend.dto.reporting.projection;

import com.cityprojects.citybackend.entity.inventory.TypeMouvementStock;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Projection JPQL : mouvement de stock joint au produit (R-INV-002).
 */
public interface MouvementValoriseProjection {

    Long getMouvementId();

    Instant getDate();

    Long getProduitId();

    String getCodeProduit();

    String getNomProduit();

    TypeMouvementStock getTypeMouvement();

    Integer getQuantite();

    /** Prix snapshote sur le mouvement ; null si absent (fallback Produit.prixUnitaire). */
    BigDecimal getPrixUnitaireMouvement();

    BigDecimal getPrixUnitaireProduit();

    String getReferenceDocument();
}

package com.cityprojects.citybackend.dto.reporting.projection;

import java.math.BigDecimal;

/**
 * Projection JPQL : agregat par client sur une periode (R-CLI-001).
 *
 * <p>Tri par {@code caTtc} desc applique cote service (limit configurable).</p>
 */
public interface TopClientProjection {
    Long getClientId();

    String getNumeroClient();

    String getNom();

    String getPrenom();

    Long getNbFactures();

    BigDecimal getCaTtc();

    BigDecimal getCaPaye();
}

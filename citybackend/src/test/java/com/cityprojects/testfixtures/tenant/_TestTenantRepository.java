package com.cityprojects.testfixtures.tenant;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository de test pour {@link _TestTenantEntity}.
 * Volontairement hors du package racine de l'application (cf. classe entite).
 */
public interface _TestTenantRepository extends JpaRepository<_TestTenantEntity, Long> {
}

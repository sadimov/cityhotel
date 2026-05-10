package com.cityprojects.citybackend.service.admin;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.dto.admin.ParametreAdminDto;
import com.cityprojects.citybackend.dto.admin.ParametreCreateAdminDto;
import com.cityprojects.citybackend.dto.admin.ParametreUpdateAdminDto;
import com.cityprojects.citybackend.entity.core.Parametre;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.repository.core.ParametreRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests Surefire (H2 + Spring) du {@link ParametreAdminService}.
 *
 * <p>Couverture :
 * <ol>
 *   <li>T1 : create() force modifiable=true.</li>
 *   <li>T2 : update() applique sur un parametre modifiable=true.</li>
 *   <li>T3 : update() refuse sur un parametre modifiable=false.</li>
 *   <li>T4 : delete() refuse sur un parametre modifiable=false.</li>
 *   <li>T5 : findByCle() insensible a la casse.</li>
 * </ol>
 *
 * <p>Note : les parametres systeme du seed Liquibase ne sont PAS presents
 * dans le profil "test" (Liquibase desactive). On insere a la main les
 * parametres modifiable=false necessaires aux tests.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class ParametreAdminServiceTests {

    @Autowired
    private ParametreAdminService parametreAdminService;

    @Autowired
    private ParametreRepository parametreRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        transactionTemplate = new TransactionTemplate(transactionManager);
        jdbcTemplate.update("DELETE FROM core.parametres");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        jdbcTemplate.update("DELETE FROM core.parametres");
    }

    @Test
    @DisplayName("T1 - create() force modifiable=true et persiste les autres champs")
    void shouldForceModifiableTrueOnCreate() {
        ParametreCreateAdminDto dto = new ParametreCreateAdminDto(
                "notification.email.support", "support@city-hotel.local",
                "Email du support technique", "notification");

        ParametreAdminDto created = transactionTemplate.execute(s ->
                parametreAdminService.create(dto));

        assertNotNull(created);
        assertNotNull(created.parametreId());
        assertEquals("notification.email.support", created.cle());
        assertEquals("support@city-hotel.local", created.valeur());
        assertTrue(Boolean.TRUE.equals(created.modifiable()),
                "create() doit FORCER modifiable=true");
        assertEquals("notification", created.categorie());
    }

    @Test
    @DisplayName("T2 - update() applique sur parametre modifiable=true")
    void shouldUpdateModifiableParametre() {
        ParametreAdminDto created = transactionTemplate.execute(s ->
                parametreAdminService.create(new ParametreCreateAdminDto(
                        "audit.retention.days", "365",
                        "Retention audit en jours", "audit")));

        ParametreUpdateAdminDto patch = new ParametreUpdateAdminDto(
                "180", "Nouvelle retention", null);

        ParametreAdminDto updated = transactionTemplate.execute(s ->
                parametreAdminService.update(created.parametreId(), patch));

        assertEquals("180", updated.valeur());
        assertEquals("Nouvelle retention", updated.description());
        // categorie intact (patch null)
        assertEquals("audit", updated.categorie());
        // cle reste immuable
        assertEquals("audit.retention.days", updated.cle());
    }

    @Test
    @DisplayName("T3 - update() refuse sur parametre modifiable=false -> BusinessException")
    void shouldRefuseUpdateNonModifiable() {
        // Insert direct d'un parametre systeme (modifiable=false)
        Parametre systeme = new Parametre();
        systeme.setCle("app.timezone");
        systeme.setValeur("Africa/Nouakchott");
        systeme.setModifiable(Boolean.FALSE);
        systeme.setCategorie("system");
        Long id = parametreRepository.saveAndFlush(systeme).getParametreId();

        ParametreUpdateAdminDto patch = new ParametreUpdateAdminDto("UTC", null, null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> transactionTemplate.execute(s ->
                        parametreAdminService.update(id, patch)));
        assertEquals("error.parametre.notModifiable", ex.getMessage());

        // Verifier non modifie
        Parametre reloaded = parametreRepository.findById(id).orElseThrow();
        assertEquals("Africa/Nouakchott", reloaded.getValeur());
    }

    @Test
    @DisplayName("T4 - delete() refuse sur parametre modifiable=false -> BusinessException")
    void shouldRefuseDeleteNonModifiable() {
        Parametre systeme = new Parametre();
        systeme.setCle("app.devise");
        systeme.setValeur("MRU");
        systeme.setModifiable(Boolean.FALSE);
        systeme.setCategorie("system");
        Long id = parametreRepository.saveAndFlush(systeme).getParametreId();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> transactionTemplate.executeWithoutResult(s ->
                        parametreAdminService.delete(id)));
        assertEquals("error.parametre.notModifiable", ex.getMessage());

        // Toujours present en base
        assertTrue(parametreRepository.findById(id).isPresent());
    }

    @Test
    @DisplayName("T5 - findByCle() insensible a la casse")
    void shouldFindByCleIgnoreCase() {
        transactionTemplate.execute(s -> parametreAdminService.create(
                new ParametreCreateAdminDto("App.Timezone", "Africa/Nouakchott",
                        "Test", "system")));

        ParametreAdminDto byUpper = parametreAdminService.findByCle("APP.TIMEZONE");
        ParametreAdminDto byLower = parametreAdminService.findByCle("app.timezone");
        assertEquals(byUpper.parametreId(), byLower.parametreId());
        // Stocke avec la casse d'origine
        assertEquals("App.Timezone", byUpper.cle());
    }
}

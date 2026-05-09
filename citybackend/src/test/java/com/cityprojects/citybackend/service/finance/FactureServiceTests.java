package com.cityprojects.citybackend.service.finance;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.dto.finance.FactureCreateDto;
import com.cityprojects.citybackend.dto.finance.FactureDto;
import com.cityprojects.citybackend.dto.finance.LigneFactureCreateDto;
import com.cityprojects.citybackend.entity.core.DBUser;
import com.cityprojects.citybackend.entity.core.Hotel;
import com.cityprojects.citybackend.entity.core.Role;
import com.cityprojects.citybackend.entity.finance.StatutFacture;
import com.cityprojects.citybackend.entity.finance.TypeLigneFacture;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.exception.ResourceNotFoundException;
import com.cityprojects.citybackend.repository.core.DBUserRepository;
import com.cityprojects.citybackend.repository.core.HotelRepository;
import com.cityprojects.citybackend.repository.core.RoleRepository;
import com.cityprojects.citybackend.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests Surefire (rapides, en H2) du {@link FactureService}.
 *
 * <h3>Couverture</h3>
 * <ol>
 *   <li>T1 : create() minimal (sans ligne) -&gt; numero FACT-{annee}-MR-000001 + statut BROUILLON.</li>
 *   <li>T2 : create() avec lignes -&gt; recalcul automatique des montants HT/TTC.</li>
 *   <li>T3 : transition BROUILLON -&gt; EMISE via emettre().</li>
 *   <li>T4 : isolation cross-tenant -&gt; findById() depuis un autre hotel renvoie 404 (ResourceNotFoundException).</li>
 *   <li>T5 : arrondi BigDecimal HALF_UP -&gt; 2.50 * 3 = 7.50 (pas 7.49 ou 7.51).</li>
 * </ol>
 */
@SpringBootTest
@ActiveProfiles("test")
class FactureServiceTests {

    @Autowired
    private FactureService factureService;

    @Autowired
    private HotelRepository hotelRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private DBUserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;
    private Long hotelMrId;
    private Long hotelFrId;
    private DBUser userMr;
    private int currentYear;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        TenantContext.clear();
        SecurityContextHolder.clearContext();
        currentYear = LocalDate.now().getYear();

        // Cleanup ordonne
        jdbcTemplate.update("DELETE FROM finance.affectations_paiements");
        jdbcTemplate.update("DELETE FROM finance.operations_comptes");
        jdbcTemplate.update("DELETE FROM finance.paiements");
        jdbcTemplate.update("DELETE FROM finance.lignes_factures");
        jdbcTemplate.update("DELETE FROM finance.factures");
        jdbcTemplate.update("DELETE FROM finance.comptes");
        jdbcTemplate.update("DELETE FROM finance.numerotation_sequence");
        jdbcTemplate.update("DELETE FROM core.dbusers");
        jdbcTemplate.update("DELETE FROM core.hotels");
        jdbcTemplate.update("DELETE FROM core.roles");

        Hotel mr = new Hotel("MR1", "Hotel Mauritanie");
        mr.setCodePays("MR");
        hotelMrId = hotelRepository.saveAndFlush(mr).getHotelId();

        Hotel fr = new Hotel("FR1", "Hotel France");
        fr.setCodePays("FR");
        hotelFrId = hotelRepository.saveAndFlush(fr).getHotelId();

        Role gerant = roleRepository.saveAndFlush(new Role("GERANT", "Gerant"));

        userMr = new DBUser("gerant1", "gerant1@h1.test", "$2a$12$placeholder",
                "Sidi", "Cheikh", mr, gerant);
        userMr.setActif(Boolean.TRUE);
        userMr.setCompteVerrouille(Boolean.FALSE);
        userMr = userRepository.saveAndFlush(userMr);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
        jdbcTemplate.update("DELETE FROM finance.affectations_paiements");
        jdbcTemplate.update("DELETE FROM finance.operations_comptes");
        jdbcTemplate.update("DELETE FROM finance.paiements");
        jdbcTemplate.update("DELETE FROM finance.lignes_factures");
        jdbcTemplate.update("DELETE FROM finance.factures");
        jdbcTemplate.update("DELETE FROM finance.comptes");
        jdbcTemplate.update("DELETE FROM finance.numerotation_sequence");
        jdbcTemplate.update("DELETE FROM core.dbusers");
        jdbcTemplate.update("DELETE FROM core.hotels");
        jdbcTemplate.update("DELETE FROM core.roles");
    }

    private void authenticateAs(DBUser user, String roleCode) {
        UserPrincipal principal = UserPrincipal.create(user, Collections.singletonList(
                new SimpleGrantedAuthority("ROLE_" + roleCode)));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @Test
    @DisplayName("T1 - create() minimal (sans lignes) genere numero FACT-{annee}-MR-000001 et statut BROUILLON")
    void shouldCreateFactureMinimal() {
        TenantContext.set(hotelMrId);
        authenticateAs(userMr, "GERANT");

        FactureCreateDto dto = new FactureCreateDto(null, null, null, null, null, null,
                null, null, null, null, List.of());

        FactureDto created = transactionTemplate.execute(t -> factureService.create(dto));

        assertNotNull(created);
        assertNotNull(created.factureId());
        assertEquals(String.format("FACT-%d-MR-000001", currentYear), created.numeroFacture(),
                "Le numero doit suivre FACT-{annee}-{codePays}-{6 chiffres}");
        assertEquals(StatutFacture.BROUILLON, created.statut());
        assertEquals(0, created.montantTtc().compareTo(BigDecimal.ZERO));
        assertEquals("MRU", created.devise());
    }

    @Test
    @DisplayName("T2 - create() avec lignes -> recalcul automatique des montants (somme des lignes)")
    void shouldRecalculateMontantsFromLignes() {
        TenantContext.set(hotelMrId);
        authenticateAs(userMr, "GERANT");

        // 2 lignes : 3 nuits x 10000 + 1 produit x 500 = 30000 + 500 = 30500
        LigneFactureCreateDto l1 = new LigneFactureCreateDto(
                TypeLigneFacture.NUITEE, null, null, null, null,
                "Nuit standard", BigDecimal.valueOf(3),
                BigDecimal.valueOf(10000), BigDecimal.ZERO, null);
        LigneFactureCreateDto l2 = new LigneFactureCreateDto(
                TypeLigneFacture.DIVERS, null, null, null, null,
                "Mini-bar", BigDecimal.ONE,
                BigDecimal.valueOf(500), BigDecimal.ZERO, null);

        FactureCreateDto dto = new FactureCreateDto(null, null, null, null, null, null,
                null, null, null, null, List.of(l1, l2));

        FactureDto created = transactionTemplate.execute(t -> factureService.create(dto));

        assertEquals(2, created.lignes().size());
        assertEquals(0, created.montantHt().compareTo(BigDecimal.valueOf(30500.00)),
                "3*10000 + 1*500 = 30500 HT");
        assertEquals(0, created.montantTtc().compareTo(BigDecimal.valueOf(30500.00)),
                "Pas de TVA -> TTC = HT");
        assertEquals(0, created.montantPaye().compareTo(BigDecimal.ZERO));
        assertEquals(0, created.montantRestant().compareTo(BigDecimal.valueOf(30500.00)),
                "montantRestant = TTC - paye");
    }

    @Test
    @DisplayName("T3 - transition BROUILLON -> EMISE via emettre()")
    void shouldEmettreFacture() {
        TenantContext.set(hotelMrId);
        authenticateAs(userMr, "GERANT");

        FactureDto created = transactionTemplate.execute(t -> factureService.create(
                new FactureCreateDto(null, null, null, null, null, null, null, null,
                        null, null, List.of())));
        assertEquals(StatutFacture.BROUILLON, created.statut());

        FactureDto emise = transactionTemplate.execute(t -> factureService.emettre(created.factureId()));
        assertEquals(StatutFacture.EMISE, emise.statut());

        // Tentative de re-emission -> BusinessException
        assertThrows(BusinessException.class, () ->
                transactionTemplate.execute(t -> factureService.emettre(created.factureId())));
    }

    @Test
    @DisplayName("T4 - isolation cross-tenant : facture creee en MR, lecture en FR -> 404")
    void shouldIsolateCrossTenantViaTenantId() {
        // Cree une facture dans hotel MR
        TenantContext.set(hotelMrId);
        authenticateAs(userMr, "GERANT");
        FactureDto created = transactionTemplate.execute(t -> factureService.create(
                new FactureCreateDto(null, null, null, null, null, null, null, null,
                        null, null, List.of())));
        Long factureId = created.factureId();
        TenantContext.clear();
        SecurityContextHolder.clearContext();

        // Bascule sur tenant FR -> findById doit lever ResourceNotFoundException
        TenantContext.set(hotelFrId);
        // Authentifie un user du FR pour passer le @RequireTenant + le currentUserId si jamais
        Role gerant = roleRepository.findByRoleCode("GERANT").orElseThrow();
        Hotel fr = hotelRepository.findById(hotelFrId).orElseThrow();
        DBUser userFr = new DBUser("gerantFR", "gerantFR@h2.test", "$2a$12$placeholder",
                "Pierre", "Dupont", fr, gerant);
        userFr.setActif(Boolean.TRUE);
        userFr.setCompteVerrouille(Boolean.FALSE);
        DBUser persistedFr = userRepository.saveAndFlush(userFr);
        authenticateAs(persistedFr, "GERANT");

        assertThrows(ResourceNotFoundException.class, () ->
                transactionTemplate.execute(t -> factureService.findById(factureId)));
    }

    @Test
    @DisplayName("T5 - arrondi BigDecimal HALF_UP : 2.555 * 3 = 7.67 (pas 7.66)")
    void shouldRoundBigDecimalHalfUp() {
        TenantContext.set(hotelMrId);
        authenticateAs(userMr, "GERANT");

        // 3 unites a 2.555 = 7.665 -> arrondi HALF_UP = 7.67
        LigneFactureCreateDto l1 = new LigneFactureCreateDto(
                TypeLigneFacture.DIVERS, null, null, null, null,
                "Test arrondi", BigDecimal.valueOf(3),
                new BigDecimal("2.555"), BigDecimal.ZERO, null);

        FactureCreateDto dto = new FactureCreateDto(null, null, null, null, null, null,
                null, null, null, null, List.of(l1));

        FactureDto created = transactionTemplate.execute(t -> factureService.create(dto));
        // 3 * 2.555 = 7.665, HALF_UP scale 2 -> 7.67
        assertEquals(0, created.montantHt().compareTo(new BigDecimal("7.67")),
                "Arrondi HALF_UP : 7.665 -> 7.67, pas 7.66 (HALF_DOWN)");
    }
}

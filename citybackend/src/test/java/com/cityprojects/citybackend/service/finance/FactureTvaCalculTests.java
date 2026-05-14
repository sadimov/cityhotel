package com.cityprojects.citybackend.service.finance;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.dto.finance.FactureCreateDto;
import com.cityprojects.citybackend.dto.finance.FactureDto;
import com.cityprojects.citybackend.dto.finance.LigneFactureCreateDto;
import com.cityprojects.citybackend.dto.finance.TauxTvaConfigDto;
import com.cityprojects.citybackend.entity.core.DBUser;
import com.cityprojects.citybackend.entity.core.Hotel;
import com.cityprojects.citybackend.entity.core.Role;
import com.cityprojects.citybackend.entity.finance.TypeLigneFacture;
import com.cityprojects.citybackend.entity.finance.TypeServiceTva;
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
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests B4 : verifie que {@code LigneFacture.tauxTva} est resolu par
 * {@link TauxTvaConfigService} quand le DTO ne fournit pas de taux
 * explicite (et que l'override DTO est respecte sinon).
 */
@SpringBootTest
@ActiveProfiles("test")
class FactureTvaCalculTests {

    @Autowired private FactureService factureService;
    @Autowired private TauxTvaConfigService tauxTvaConfigService;
    @Autowired private HotelRepository hotelRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private DBUserRepository userRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;

    private TransactionTemplate tx;
    private Long hotelId;
    private DBUser user;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(transactionManager);
        TenantContext.clear();
        SecurityContextHolder.clearContext();
        cleanAll();

        Hotel h = new Hotel("MR2", "Hotel TVA Test");
        h.setCodePays("MR");
        hotelId = hotelRepository.saveAndFlush(h).getHotelId();
        Role gerant = roleRepository.saveAndFlush(new Role("GERANT", "Gerant"));
        DBUser u = new DBUser("gerantTva", "g@h.test", "$2a$12$placeholder",
                "Gerant", "Tva", h, gerant);
        u.setActif(Boolean.TRUE);
        u.setCompteVerrouille(Boolean.FALSE);
        user = userRepository.saveAndFlush(u);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
        cleanAll();
    }

    private void cleanAll() {
        jdbcTemplate.update("DELETE FROM finance.taux_tva_config");
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

    private void authenticate() {
        UserPrincipal p = UserPrincipal.create(user, Collections.singletonList(
                new SimpleGrantedAuthority("ROLE_GERANT")));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities()));
    }

    @Test
    @DisplayName("T1 - DTO tauxTva null + config 16% -> ligne facturee a 16%")
    void shouldResolveTauxFromConfigWhenNull() {
        TenantContext.set(hotelId);
        authenticate();

        // Configure RESTAURATION a 16% (defaut applicatif mais on materialise pour clarte).
        tx.execute(t -> tauxTvaConfigService.update(
                TypeServiceTva.RESTAURATION, new BigDecimal("16.00"), null, null));

        // Ligne PRODUIT (mappe a RESTAURATION) avec tauxTva = null -> doit reprendre 16%
        LigneFactureCreateDto ligne = new LigneFactureCreateDto(
                TypeLigneFacture.PRODUIT, null, null, null, null,
                "Cafe", BigDecimal.ONE, new BigDecimal("100.00"),
                null /* tauxTva : null = laisser le service decider */,
                null);
        FactureCreateDto dto = new FactureCreateDto(null, null, null, null, null, null,
                null, null, null, null, List.of(ligne));
        FactureDto created = tx.execute(t -> factureService.create(dto));

        assertNotNull(created);
        assertEquals(1, created.lignes().size());
        // HT = 100, TVA 16% = 16, TTC = 116
        assertEquals(0, created.lignes().get(0).montantHt().compareTo(new BigDecimal("100.00")));
        assertEquals(0, created.lignes().get(0).montantTva().compareTo(new BigDecimal("16.00")));
        assertEquals(0, created.lignes().get(0).montantTtc().compareTo(new BigDecimal("116.00")));
        assertEquals(0, created.montantHt().compareTo(new BigDecimal("100.00")));
        assertEquals(0, created.montantTva().compareTo(new BigDecimal("16.00")));
        assertEquals(0, created.montantTtc().compareTo(new BigDecimal("116.00")));
    }

    @Test
    @DisplayName("T2 - DTO tauxTva = 0 explicite -> override respecte (pas de TVA)")
    void shouldRespectExplicitZeroOverride() {
        TenantContext.set(hotelId);
        authenticate();

        // Configure RESTAURATION a 16%
        tx.execute(t -> tauxTvaConfigService.update(
                TypeServiceTva.RESTAURATION, new BigDecimal("16.00"), null, null));

        // Ligne PRODUIT avec tauxTva = 0 explicite -> override (compat tests existants)
        LigneFactureCreateDto ligne = new LigneFactureCreateDto(
                TypeLigneFacture.PRODUIT, null, null, null, null,
                "Cafe", BigDecimal.ONE, new BigDecimal("100.00"),
                BigDecimal.ZERO, null);
        FactureCreateDto dto = new FactureCreateDto(null, null, null, null, null, null,
                null, null, null, null, List.of(ligne));
        FactureDto created = tx.execute(t -> factureService.create(dto));

        // Override respecte : pas de TVA
        assertEquals(0, created.montantHt().compareTo(new BigDecimal("100.00")));
        assertEquals(0, created.montantTva().compareTo(BigDecimal.ZERO));
        assertEquals(0, created.montantTtc().compareTo(new BigDecimal("100.00")));
    }

    @Test
    @DisplayName("T3 - DTO tauxTva null sur NUITEE + defaut 0% -> pas de TVA")
    void hebergementDefaultZero() {
        TenantContext.set(hotelId);
        authenticate();

        // Aucune config seedee -> fallback HEBERGEMENT_NUITEE.defaultTaux() = 0%
        TauxTvaConfigDto cfg = tauxTvaConfigService.findByType(TypeServiceTva.HEBERGEMENT_NUITEE);
        assertEquals(0, cfg.taux().compareTo(BigDecimal.ZERO));

        LigneFactureCreateDto ligne = new LigneFactureCreateDto(
                TypeLigneFacture.NUITEE, null, null, null, null,
                "Nuit standard", BigDecimal.ONE, new BigDecimal("15000.00"),
                null, null);
        FactureCreateDto dto = new FactureCreateDto(null, null, null, null, null, null,
                null, null, null, null, List.of(ligne));
        FactureDto created = tx.execute(t -> factureService.create(dto));

        assertEquals(0, created.montantTva().compareTo(BigDecimal.ZERO));
        assertEquals(0, created.montantTtc().compareTo(new BigDecimal("15000.00")));
    }

    @Test
    @DisplayName("T4 - DTO tauxTva explicite 7.5 -> override exact respecte")
    void shouldRespectArbitraryOverride() {
        TenantContext.set(hotelId);
        authenticate();

        LigneFactureCreateDto ligne = new LigneFactureCreateDto(
                TypeLigneFacture.SERVICE, null, null, null, null,
                "Service special", BigDecimal.ONE, new BigDecimal("200.00"),
                new BigDecimal("7.5"), null);
        FactureCreateDto dto = new FactureCreateDto(null, null, null, null, null, null,
                null, null, null, null, List.of(ligne));
        FactureDto created = tx.execute(t -> factureService.create(dto));

        // 200 * 7.5% = 15.00, TTC = 215.00
        assertEquals(0, created.montantHt().compareTo(new BigDecimal("200.00")));
        assertEquals(0, created.montantTva().compareTo(new BigDecimal("15.00")));
        assertEquals(0, created.montantTtc().compareTo(new BigDecimal("215.00")));
    }
}

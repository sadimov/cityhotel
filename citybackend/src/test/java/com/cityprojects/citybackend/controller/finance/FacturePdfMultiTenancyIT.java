package com.cityprojects.citybackend.controller.finance;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.entity.core.DBUser;
import com.cityprojects.citybackend.entity.core.Hotel;
import com.cityprojects.citybackend.entity.core.Role;
import com.cityprojects.citybackend.entity.finance.Facture;
import com.cityprojects.citybackend.entity.finance.StatutFacture;
import com.cityprojects.citybackend.entity.finance.TypeFacture;
import com.cityprojects.citybackend.repository.core.DBUserRepository;
import com.cityprojects.citybackend.repository.core.HotelRepository;
import com.cityprojects.citybackend.repository.core.RoleRepository;
import com.cityprojects.citybackend.repository.finance.FactureRepository;
import com.cityprojects.citybackend.security.JwtTokenProvider;
import com.cityprojects.citybackend.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test Failsafe multi-tenant pour {@code GET /api/finance/factures/{id}/pdf}
 * (Bloc B6).
 *
 * <h3>Couverture</h3>
 * <p>Un utilisateur de l'hotel A NE DOIT PAS pouvoir generer le PDF d'une
 * facture appartenant a l'hotel B (filtre Hibernate {@code @TenantId}
 * actif sur {@code Facture.findById}). Le controller doit renvoyer 404.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class FacturePdfMultiTenancyIT {

    @Autowired private WebApplicationContext webApplicationContext;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private DBUserRepository userRepository;
    @Autowired private HotelRepository hotelRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private FactureRepository factureRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;

    private MockMvc mockMvc;
    private TransactionTemplate transactionTemplate;
    private DBUser userGerantHotelA;
    private DBUser userGerantHotelB;
    private Long hotelAId;
    private Long hotelBId;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        transactionTemplate = new TransactionTemplate(transactionManager);
        cleanAll();

        Hotel a = new Hotel("MTA", "Hotel A Mauritanie");
        a.setCodePays("MR");
        hotelAId = hotelRepository.saveAndFlush(a).getHotelId();

        Hotel b = new Hotel("MTB", "Hotel B France");
        b.setCodePays("FR");
        hotelBId = hotelRepository.saveAndFlush(b).getHotelId();

        Role gerant = roleRepository.saveAndFlush(new Role("GERANT", "Gerant"));

        userGerantHotelA = userRepository.saveAndFlush(buildUser(
                "gerantA", "gerantA@hotel-a.test", a, gerant));
        userGerantHotelB = userRepository.saveAndFlush(buildUser(
                "gerantB", "gerantB@hotel-b.test", b, gerant));

        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        cleanAll();
    }

    private void cleanAll() {
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

    private DBUser buildUser(String username, String email, Hotel hotel, Role role) {
        DBUser user = new DBUser(username, email,
                "$2a$12$placeholderhashplaceholderhashplaceholderhashplacehash",
                "Test", "User", hotel, role);
        user.setActif(Boolean.TRUE);
        user.setCompteVerrouille(Boolean.FALSE);
        return user;
    }

    private String jwtFor(DBUser user) {
        UserPrincipal principal = UserPrincipal.create(user, Collections.singletonList(
                new SimpleGrantedAuthority("ROLE_" + user.getRole().getRoleCode())));
        return jwtTokenProvider.generateTokenForUser(principal);
    }

    private Long createFactureInTenant(Long tenantId, Long userId) {
        try {
            TenantContext.set(tenantId);
            return transactionTemplate.execute(s -> {
                Facture f = new Facture();
                f.setNumeroFacture("FACT-CROSS-" + System.nanoTime());
                f.setTypeFacture(TypeFacture.FACTURE);
                f.setDateFacture(LocalDate.of(2026, 5, 6));
                f.setStatut(StatutFacture.EMISE);
                f.setDevise("MRU");
                f.setUserId(userId);
                f.setMontantHt(BigDecimal.ZERO);
                f.setMontantTva(BigDecimal.ZERO);
                f.setMontantTtc(BigDecimal.ZERO);
                f.setMontantPaye(BigDecimal.ZERO);
                return factureRepository.save(f).getFactureId();
            });
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("Cross-tenant : user hotel B ne peut PAS generer le PDF d'une facture hotel A (404)")
    void crossTenantInterdit() throws Exception {
        // Facture cree dans le tenant A
        Long factureIdA = createFactureInTenant(hotelAId, userGerantHotelA.getUserId());

        // Tente de generer le PDF avec un JWT du tenant B
        String jwt = jwtFor(userGerantHotelB);
        mockMvc.perform(get("/api/finance/factures/" + factureIdA + "/pdf")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Same-tenant : user hotel A peut generer le PDF de SA facture (200)")
    void sameTenantOk() throws Exception {
        Long factureIdA = createFactureInTenant(hotelAId, userGerantHotelA.getUserId());

        String jwt = jwtFor(userGerantHotelA);
        mockMvc.perform(get("/api/finance/factures/" + factureIdA + "/pdf")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk());
    }
}

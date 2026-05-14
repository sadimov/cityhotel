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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Collections;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test d'integration Failsafe : valide la chaine complete
 * JWT &rarr; {@code @PreAuthorize} &rarr; {@code @TenantId} &rarr; DB sur le
 * controller facture (Tour 19).
 *
 * <h3>Cas couverts</h3>
 * <ol>
 *   <li>T1 : GERANT cree une facture -&gt; 201, pas de hotelId expose,
 *       statut BROUILLON, numero FACT-{annee}-MR-000001.</li>
 *   <li>T2 : MAGASIN tente POST /api/finance/factures -&gt; 403 (matrice n'inclut
 *       pas MAGASIN pour finance).</li>
 *   <li>T3 : JWT hotel FR lit facture hotel MR -&gt; 404 (Hibernate filtre via @TenantId).</li>
 * </ol>
 */
@SpringBootTest
@ActiveProfiles("test")
class FactureControllerIT {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private DBUserRepository userRepository;

    @Autowired
    private HotelRepository hotelRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private FactureRepository factureRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private MockMvc mockMvc;
    private TransactionTemplate transactionTemplate;

    private DBUser userGerantHotelMr;
    private DBUser userMagasinHotelMr;
    private DBUser userGerantHotelFr;

    private Long hotelMrId;
    private Long hotelFrId;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        transactionTemplate = new TransactionTemplate(transactionManager);

        // Cleanup ordonne
        cleanAll();

        Hotel mr = new Hotel("MR1", "Hotel Mauritanie");
        mr.setCodePays("MR");
        hotelMrId = hotelRepository.saveAndFlush(mr).getHotelId();

        Hotel fr = new Hotel("FR1", "Hotel France");
        fr.setCodePays("FR");
        hotelFrId = hotelRepository.saveAndFlush(fr).getHotelId();

        Role gerant = roleRepository.saveAndFlush(new Role("GERANT", "Gerant"));
        Role magasin = roleRepository.saveAndFlush(new Role("MAGASIN", "Magasin"));

        userGerantHotelMr = userRepository.saveAndFlush(buildUser(
                "gerantMR", "gerantMR@h1.test", "Sidi", "Cheikh", mr, gerant));
        userMagasinHotelMr = userRepository.saveAndFlush(buildUser(
                "magasinMR", "magasinMR@h1.test", "Karim", "Sow", mr, magasin));
        userGerantHotelFr = userRepository.saveAndFlush(buildUser(
                "gerantFR", "gerantFR@h2.test", "Pierre", "Dupont", fr, gerant));

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

    private String jwtFor(DBUser user) {
        UserPrincipal principal = UserPrincipal.create(user, Collections.singletonList(
                new SimpleGrantedAuthority("ROLE_" + user.getRole().getRoleCode())));
        return jwtTokenProvider.generateTokenForUser(principal);
    }

    private static DBUser buildUser(String username, String email, String prenom,
                                    String nom, Hotel hotel, Role role) {
        DBUser user = new DBUser(username, email, "$2a$12$placeholderhashplaceholderhashplaceholderhashplacehash",
                prenom, nom, hotel, role);
        user.setActif(Boolean.TRUE);
        user.setCompteVerrouille(Boolean.FALSE);
        return user;
    }

    private Long createFactureInTenant(Long hotelId) {
        try {
            TenantContext.set(hotelId);
            return transactionTemplate.execute(s -> {
                Facture f = new Facture();
                f.setNumeroFacture("FACT-SEED-" + System.nanoTime());
                f.setTypeFacture(TypeFacture.FACTURE);
                f.setDateFacture(LocalDate.now());
                f.setStatut(StatutFacture.BROUILLON);
                f.setDevise("MRU");
                f.setUserId(userGerantHotelMr.getUserId());
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
    @DisplayName("T1 - GERANT hotel MR cree une facture : 201, pas de hotelId expose, statut BROUILLON")
    void shouldCreateFactureWhenGerant() throws Exception {
        String jwt = jwtFor(userGerantHotelMr);
        String body = "{"
                + "\"devise\":\"MRU\","
                + "\"commentaires\":\"Test creation IT\","
                + "\"lignes\":[]"
                + "}";

        mockMvc.perform(post("/api/finance/factures")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(body.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isCreated())
                // Tour 39 : ApiResponseBodyAdvice wrappe toutes les reponses dans
                // { success, message, data: { ... } }. Les anciennes assertions
                // sur $.factureId sont remplacees par $.data.factureId.
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.factureId").exists())
                .andExpect(jsonPath("$.data.numeroFacture").exists())
                .andExpect(jsonPath("$.data.statut").value("BROUILLON"))
                .andExpect(jsonPath("$.data.devise").value("MRU"))
                // Pas de hotelId expose dans le DTO (regle CLAUDE.md)
                .andExpect(jsonPath("$.data.hotelId").doesNotExist());
    }

    @Test
    @DisplayName("T2 - MAGASIN tente POST /api/finance/factures : 403 (n'a pas le role GERANT/RECEPTION)")
    void shouldDenyAccessForMagasin() throws Exception {
        String jwt = jwtFor(userMagasinHotelMr);
        String body = "{\"devise\":\"MRU\",\"lignes\":[]}";

        mockMvc.perform(post("/api/finance/factures")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("T3 - JWT hotel FR lit facture de hotel MR : 404 (Hibernate filtre via @TenantId)")
    void shouldReturn404ForCrossTenantRead() throws Exception {
        // Cree une facture dans hotel MR via JPA direct
        Long mrFactureId = createFactureInTenant(hotelMrId);

        // Tente de la lire avec un JWT du hotel FR
        String jwt = jwtFor(userGerantHotelFr);

        mockMvc.perform(get("/api/finance/factures/" + mrFactureId)
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isNotFound());
    }
}

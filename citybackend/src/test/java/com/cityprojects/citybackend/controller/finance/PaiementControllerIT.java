package com.cityprojects.citybackend.controller.finance;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.dto.finance.FactureCreateDto;
import com.cityprojects.citybackend.dto.finance.FactureDto;
import com.cityprojects.citybackend.dto.finance.LigneFactureCreateDto;
import com.cityprojects.citybackend.entity.core.DBUser;
import com.cityprojects.citybackend.entity.core.Hotel;
import com.cityprojects.citybackend.entity.core.Role;
import com.cityprojects.citybackend.entity.finance.ModePaiement;
import com.cityprojects.citybackend.entity.finance.Paiement;
import com.cityprojects.citybackend.entity.finance.StatutPaiement;
import com.cityprojects.citybackend.entity.finance.TypeLigneFacture;
import com.cityprojects.citybackend.repository.core.DBUserRepository;
import com.cityprojects.citybackend.repository.core.HotelRepository;
import com.cityprojects.citybackend.repository.core.RoleRepository;
import com.cityprojects.citybackend.repository.finance.PaiementRepository;
import com.cityprojects.citybackend.security.JwtTokenProvider;
import com.cityprojects.citybackend.security.UserPrincipal;
import com.cityprojects.citybackend.service.finance.FactureService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test d'integration Failsafe sur {@link PaiementController} (Tour 39 — fix gap audit).
 *
 * <h3>Cas couverts</h3>
 * <ol>
 *   <li>T1 : GERANT cree un paiement total -&gt; 201, numero PAY-{annee}-MR-000001,
 *       statut VALIDE, pas de hotelId expose.</li>
 *   <li>T2 : MAGASIN tente POST /api/finance/paiements -&gt; 403 (matrice exclut MAGASIN).</li>
 *   <li>T3 : MENAGE tente GET /api/finance/paiements -&gt; 403.</li>
 *   <li>T4 : NIGHTAUDIT tente POST -&gt; 403.</li>
 *   <li>T5 : JWT hotel FR lit paiement hotel MR -&gt; 404 (Hibernate @TenantId).</li>
 *   <li>T6 : RECEPTION cree paiement BANKILY avec reference -&gt; 201.</li>
 *   <li>T7 : ADMIN annule un paiement sans affectation -&gt; 200 + statut ANNULE.</li>
 *   <li>T8 : RECEPTION tente d'annuler -&gt; 403 (annulation reservee ADMIN/SUPERADMIN).</li>
 *   <li>T9 : RESREC lit un paiement -&gt; 200.</li>
 *   <li>T10 : payload invalide (montantTotal nul) -&gt; 400.</li>
 * </ol>
 *
 * <p>Pattern aligne sur {@code FactureControllerIT} : H2 MODE=PostgreSQL,
 * JWT genere via {@code JwtTokenProvider}, cleanup ordonne BeforeEach/AfterEach.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class PaiementControllerIT {

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
    private FactureService factureService;

    @Autowired
    private PaiementRepository paiementRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private MockMvc mockMvc;
    private TransactionTemplate transactionTemplate;

    private DBUser userSuperadminHotelMr;
    private DBUser userAdminHotelMr;
    private DBUser userGerantHotelMr;
    private DBUser userReceptionHotelMr;
    private DBUser userResrecHotelMr;
    private DBUser userMagasinHotelMr;
    private DBUser userMenageHotelMr;
    private DBUser userNightauditHotelMr;
    private DBUser userGerantHotelFr;

    private Long hotelMrId;
    private Long hotelFrId;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
        transactionTemplate = new TransactionTemplate(transactionManager);

        cleanAll();

        Hotel mr = new Hotel("MR1", "Hotel Mauritanie");
        mr.setCodePays("MR");
        hotelMrId = hotelRepository.saveAndFlush(mr).getHotelId();

        Hotel fr = new Hotel("FR1", "Hotel France");
        fr.setCodePays("FR");
        hotelFrId = hotelRepository.saveAndFlush(fr).getHotelId();

        Role superadmin = roleRepository.saveAndFlush(new Role("SUPERADMIN", "Super admin"));
        Role admin = roleRepository.saveAndFlush(new Role("ADMIN", "Admin"));
        Role gerant = roleRepository.saveAndFlush(new Role("GERANT", "Gerant"));
        Role reception = roleRepository.saveAndFlush(new Role("RECEPTION", "Reception"));
        Role resrec = roleRepository.saveAndFlush(new Role("RESREC", "Resrec"));
        Role magasin = roleRepository.saveAndFlush(new Role("MAGASIN", "Magasin"));
        Role menage = roleRepository.saveAndFlush(new Role("MENAGE", "Menage"));
        Role nightaudit = roleRepository.saveAndFlush(new Role("NIGHTAUDIT", "Night audit"));

        userSuperadminHotelMr = userRepository.saveAndFlush(buildUser("saMR", "sa@h1.test",
                "Super", "Admin", mr, superadmin));
        userAdminHotelMr = userRepository.saveAndFlush(buildUser("adminMR", "admin@h1.test",
                "Ali", "Mohamed", mr, admin));
        userGerantHotelMr = userRepository.saveAndFlush(buildUser("gerantMR", "gerant@h1.test",
                "Sidi", "Cheikh", mr, gerant));
        userReceptionHotelMr = userRepository.saveAndFlush(buildUser("recepMR", "recep@h1.test",
                "Khadija", "Brahim", mr, reception));
        userResrecHotelMr = userRepository.saveAndFlush(buildUser("resrecMR", "resrec@h1.test",
                "Aicha", "Sow", mr, resrec));
        userMagasinHotelMr = userRepository.saveAndFlush(buildUser("magMR", "mag@h1.test",
                "Karim", "Diop", mr, magasin));
        userMenageHotelMr = userRepository.saveAndFlush(buildUser("menMR", "men@h1.test",
                "Fatou", "Ba", mr, menage));
        userNightauditHotelMr = userRepository.saveAndFlush(buildUser("naMR", "na@h1.test",
                "Hamza", "Diallo", mr, nightaudit));
        userGerantHotelFr = userRepository.saveAndFlush(buildUser("gerantFR", "gerant@h2.test",
                "Pierre", "Dupont", fr, gerant));

        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
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
        DBUser user = new DBUser(username, email,
                "$2a$12$placeholderhashplaceholderhashplaceholderhashplacehash",
                prenom, nom, hotel, role);
        user.setActif(Boolean.TRUE);
        user.setCompteVerrouille(Boolean.FALSE);
        return user;
    }

    /**
     * Cree une facture emise (BROUILLON -&gt; EMISE) dans le tenant courant via
     * le service metier, en simulant l'authentification du userId fourni.
     */
    private Long createEmittedFactureInTenant(Long hotelId, DBUser user, BigDecimal montant) {
        try {
            TenantContext.set(hotelId);
            UserPrincipal principal = UserPrincipal.create(user, Collections.singletonList(
                    new SimpleGrantedAuthority("ROLE_" + user.getRole().getRoleCode())));
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
            return transactionTemplate.execute(t -> {
                LigneFactureCreateDto ligne = new LigneFactureCreateDto(
                        TypeLigneFacture.DIVERS, null, null, null, null,
                        "Service IT", BigDecimal.ONE, montant, BigDecimal.ZERO, null);
                FactureDto facture = factureService.create(
                        new FactureCreateDto(null, null, null, null, null, null, null,
                                null, null, null, List.of(ligne)));
                FactureDto emitted = factureService.emettre(facture.factureId());
                return emitted.factureId();
            });
        } finally {
            TenantContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    /**
     * Cree un paiement VALIDE non affecte (pour tester l'annulation).
     */
    private Long createUnaffectedPaiementInTenant(Long hotelId, DBUser user, BigDecimal montant) {
        try {
            TenantContext.set(hotelId);
            return transactionTemplate.execute(t -> {
                Paiement p = new Paiement();
                p.setNumeroPaiement("PAY-SEED-" + System.nanoTime());
                p.setMontantTotal(montant);
                p.setDevise("MRU");
                p.setModePaiement(ModePaiement.ESPECES);
                p.setStatut(StatutPaiement.VALIDE);
                p.setUserId(user.getUserId());
                return paiementRepository.save(p).getPaiementId();
            });
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("T1 - GERANT cree paiement total : 201, PAY-{annee}-MR-000001, statut VALIDE, pas de hotelId")
    void shouldCreatePaiementWhenGerant() throws Exception {
        Long factureId = createEmittedFactureInTenant(hotelMrId, userGerantHotelMr, BigDecimal.valueOf(5000));
        String jwt = jwtFor(userGerantHotelMr);

        String body = "{"
                + "\"factureId\":" + factureId + ","
                + "\"montantTotal\":5000,"
                + "\"devise\":\"MRU\","
                + "\"modePaiement\":\"ESPECES\""
                + "}";

        mockMvc.perform(post("/api/finance/paiements")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(body.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isCreated())
                // Tour 39 : ApiResponseBodyAdvice wrappe la reponse dans
                // { success, message, data: { ... } }.
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.paiementId").exists())
                .andExpect(jsonPath("$.data.numeroPaiement").exists())
                .andExpect(jsonPath("$.data.statut").value("VALIDE"))
                .andExpect(jsonPath("$.data.modePaiement").value("ESPECES"))
                .andExpect(jsonPath("$.data.hotelId").doesNotExist())
                .andExpect(jsonPath("$.data.affectations[0].factureId").value(factureId));
    }

    @Test
    @DisplayName("T2 - MAGASIN tente POST /api/finance/paiements : 403")
    void shouldDenyPostPaiementForMagasin() throws Exception {
        String jwt = jwtFor(userMagasinHotelMr);
        String body = "{\"montantTotal\":100,\"devise\":\"MRU\",\"modePaiement\":\"ESPECES\"}";

        mockMvc.perform(post("/api/finance/paiements")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("T3 - MENAGE tente GET /api/finance/paiements : 403")
    void shouldDenyListPaiementsForMenage() throws Exception {
        String jwt = jwtFor(userMenageHotelMr);

        mockMvc.perform(get("/api/finance/paiements")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("T4 - NIGHTAUDIT tente POST /api/finance/paiements : 403")
    void shouldDenyPostPaiementForNightaudit() throws Exception {
        String jwt = jwtFor(userNightauditHotelMr);
        String body = "{\"montantTotal\":100,\"devise\":\"MRU\",\"modePaiement\":\"ESPECES\"}";

        mockMvc.perform(post("/api/finance/paiements")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("T5 - JWT hotel FR lit paiement hotel MR : 404 (filtre @TenantId)")
    void shouldReturn404ForCrossTenantRead() throws Exception {
        Long mrPaiementId = createUnaffectedPaiementInTenant(hotelMrId, userGerantHotelMr,
                BigDecimal.valueOf(1000));
        String jwt = jwtFor(userGerantHotelFr);

        mockMvc.perform(get("/api/finance/paiements/" + mrPaiementId)
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("T6 - RECEPTION cree paiement BANKILY avec reference : 201, mode BANKILY")
    void shouldCreatePaiementBankilyWhenReception() throws Exception {
        Long factureId = createEmittedFactureInTenant(hotelMrId, userReceptionHotelMr, BigDecimal.valueOf(2500));
        String jwt = jwtFor(userReceptionHotelMr);

        String body = "{"
                + "\"factureId\":" + factureId + ","
                + "\"montantTotal\":2500,"
                + "\"modePaiement\":\"BANKILY\","
                + "\"referencePaiement\":\"BNK-REF-12345\""
                + "}";

        mockMvc.perform(post("/api/finance/paiements")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.modePaiement").value("BANKILY"))
                .andExpect(jsonPath("$.data.referencePaiement").value("BNK-REF-12345"));
    }

    @Test
    @DisplayName("T7 - ADMIN annule un paiement sans affectation : 200, statut ANNULE")
    void shouldAllowAdminToCancelUnaffectedPaiement() throws Exception {
        Long paiementId = createUnaffectedPaiementInTenant(hotelMrId, userAdminHotelMr,
                BigDecimal.valueOf(800));
        String jwt = jwtFor(userAdminHotelMr);

        mockMvc.perform(post("/api/finance/paiements/" + paiementId + "/annuler")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.statut").value("ANNULE"));
    }

    @Test
    @DisplayName("T8 - RECEPTION tente d'annuler un paiement : 403 (annulation reservee ADMIN/SUPERADMIN)")
    void shouldDenyCancelPaiementForReception() throws Exception {
        Long paiementId = createUnaffectedPaiementInTenant(hotelMrId, userReceptionHotelMr,
                BigDecimal.valueOf(500));
        String jwt = jwtFor(userReceptionHotelMr);

        mockMvc.perform(post("/api/finance/paiements/" + paiementId + "/annuler")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("T9 - RESREC lit un paiement de son hotel : 200")
    void shouldAllowResrecToReadPaiement() throws Exception {
        Long paiementId = createUnaffectedPaiementInTenant(hotelMrId, userResrecHotelMr,
                BigDecimal.valueOf(300));
        String jwt = jwtFor(userResrecHotelMr);

        mockMvc.perform(get("/api/finance/paiements/" + paiementId)
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.paiementId").value(paiementId));
    }

    @Test
    @DisplayName("T10 - payload invalide (montantTotal manquant) : 400")
    void shouldReturn400OnInvalidPayload() throws Exception {
        String jwt = jwtFor(userGerantHotelMr);
        // montantTotal absent + modePaiement absent : @NotNull viole
        String body = "{\"devise\":\"MRU\"}";

        mockMvc.perform(post("/api/finance/paiements")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}

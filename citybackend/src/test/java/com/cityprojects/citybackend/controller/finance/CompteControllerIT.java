package com.cityprojects.citybackend.controller.finance;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.entity.client.Client;
import com.cityprojects.citybackend.entity.core.DBUser;
import com.cityprojects.citybackend.entity.core.Hotel;
import com.cityprojects.citybackend.entity.core.Role;
import com.cityprojects.citybackend.repository.client.ClientRepository;
import com.cityprojects.citybackend.repository.core.DBUserRepository;
import com.cityprojects.citybackend.repository.core.HotelRepository;
import com.cityprojects.citybackend.repository.core.RoleRepository;
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

import java.util.Collections;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test d'integration Failsafe sur {@link CompteController} (Tour 39 — fix gap audit).
 *
 * <p>Le seul endpoint expose est {@code GET /api/finance/comptes/client/{id}/folio}.
 * Il sert le folio (extrait de compte auxiliaire client) consomme par la modale
 * paiement frontend (Tour 46). Le compte est cree a la volee (idempotent) si
 * inexistant.</p>
 *
 * <h3>Cas couverts</h3>
 * <ol>
 *   <li>T1 : GERANT consulte le folio d'un client de son hotel -&gt; 200, compte
 *       cree avec solde 0, operations vide.</li>
 *   <li>T2 : MAGASIN tente GET folio -&gt; 403 (matrice n'inclut pas MAGASIN
 *       pour finance).</li>
 *   <li>T3 : MENAGE tente GET folio -&gt; 403.</li>
 *   <li>T4 : NIGHTAUDIT tente GET folio -&gt; 403.</li>
 *   <li>T5 : RECEPTION consulte le folio (cas frontend Tour 46) -&gt; 200.</li>
 *   <li>T6 : RESREC consulte le folio -&gt; 200.</li>
 *   <li>T7 : isolation multi-tenant : JWT hotel FR demande folio d'un client
 *       de hotel MR -&gt; 404 (Hibernate @TenantId).</li>
 *   <li>T8 : authentification absente -&gt; 401.</li>
 * </ol>
 */
@SpringBootTest
@ActiveProfiles("test")
class CompteControllerIT {

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
    private ClientRepository clientRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private MockMvc mockMvc;
    private TransactionTemplate transactionTemplate;

    private DBUser userGerantHotelMr;
    private DBUser userReceptionHotelMr;
    private DBUser userResrecHotelMr;
    private DBUser userMagasinHotelMr;
    private DBUser userMenageHotelMr;
    private DBUser userNightauditHotelMr;
    private DBUser userGerantHotelFr;

    private Long hotelMrId;
    private Long hotelFrId;
    private Long clientMrId;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        transactionTemplate = new TransactionTemplate(transactionManager);

        cleanAll();

        Hotel mr = new Hotel("MR1", "Hotel Mauritanie");
        mr.setCodePays("MR");
        hotelMrId = hotelRepository.saveAndFlush(mr).getHotelId();

        Hotel fr = new Hotel("FR1", "Hotel France");
        fr.setCodePays("FR");
        hotelFrId = hotelRepository.saveAndFlush(fr).getHotelId();

        Role gerant = roleRepository.saveAndFlush(new Role("GERANT", "Gerant"));
        Role reception = roleRepository.saveAndFlush(new Role("RECEPTION", "Reception"));
        Role resrec = roleRepository.saveAndFlush(new Role("RESREC", "Resrec"));
        Role magasin = roleRepository.saveAndFlush(new Role("MAGASIN", "Magasin"));
        Role menage = roleRepository.saveAndFlush(new Role("MENAGE", "Menage"));
        Role nightaudit = roleRepository.saveAndFlush(new Role("NIGHTAUDIT", "Night audit"));

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

        // Cree un client cote hotel MR (seed). Le compte sera cree a la volee
        // par OperationCompteService.findFolio (idempotent).
        clientMrId = createClientInTenant(hotelMrId, "CLI-IT-MR-001", "Alpha", "Tester");

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
        jdbcTemplate.update("DELETE FROM client.clients");
        jdbcTemplate.update("DELETE FROM client.societes");
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

    private Long createClientInTenant(Long hotelId, String numero, String prenom, String nom) {
        try {
            TenantContext.set(hotelId);
            return transactionTemplate.execute(t -> {
                Client client = new Client();
                client.setNumeroClient(numero);
                client.setPrenom(prenom);
                client.setNom(nom);
                client.setActif(Boolean.TRUE);
                return clientRepository.save(client).getClientId();
            });
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("T1 - GERANT consulte folio client de son hotel : 200, soldes a 0, operations vide")
    void shouldReturnFolioWhenGerant() throws Exception {
        String jwt = jwtFor(userGerantHotelMr);

        mockMvc.perform(get("/api/finance/comptes/client/" + clientMrId + "/folio")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                // Tour 39 : ApiResponseBodyAdvice wrappe la reponse dans
                // { success, message, data: FolioDto }.
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.clientId").value(clientMrId))
                .andExpect(jsonPath("$.data.compteId").exists())
                .andExpect(jsonPath("$.data.soldeOuverture").value(0))
                .andExpect(jsonPath("$.data.soldeCloture").value(0))
                .andExpect(jsonPath("$.data.totalDebits").value(0))
                .andExpect(jsonPath("$.data.totalCredits").value(0))
                .andExpect(jsonPath("$.data.operations").isArray())
                .andExpect(jsonPath("$.data.operations.length()").value(0));
    }

    @Test
    @DisplayName("T2 - MAGASIN tente GET folio : 403")
    void shouldDenyFolioForMagasin() throws Exception {
        String jwt = jwtFor(userMagasinHotelMr);

        mockMvc.perform(get("/api/finance/comptes/client/" + clientMrId + "/folio")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("T3 - MENAGE tente GET folio : 403")
    void shouldDenyFolioForMenage() throws Exception {
        String jwt = jwtFor(userMenageHotelMr);

        mockMvc.perform(get("/api/finance/comptes/client/" + clientMrId + "/folio")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("T4 - NIGHTAUDIT tente GET folio : 403")
    void shouldDenyFolioForNightaudit() throws Exception {
        String jwt = jwtFor(userNightauditHotelMr);

        mockMvc.perform(get("/api/finance/comptes/client/" + clientMrId + "/folio")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("T5 - RECEPTION consulte le folio (cas frontend Tour 46) : 200")
    void shouldAllowFolioForReception() throws Exception {
        String jwt = jwtFor(userReceptionHotelMr);

        mockMvc.perform(get("/api/finance/comptes/client/" + clientMrId + "/folio")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.clientId").value(clientMrId));
    }

    @Test
    @DisplayName("T6 - RESREC consulte le folio : 200")
    void shouldAllowFolioForResrec() throws Exception {
        String jwt = jwtFor(userResrecHotelMr);

        mockMvc.perform(get("/api/finance/comptes/client/" + clientMrId + "/folio")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.clientId").value(clientMrId));
    }

    @Test
    @DisplayName("T7 - JWT hotel FR demande folio d'un client hotel MR : 404 (filtre @TenantId)")
    void shouldReturn404OnCrossTenantFolio() throws Exception {
        String jwt = jwtFor(userGerantHotelFr);

        mockMvc.perform(get("/api/finance/comptes/client/" + clientMrId + "/folio")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("T8 - aucun JWT : 401")
    void shouldReturn401WhenNoToken() throws Exception {
        mockMvc.perform(get("/api/finance/comptes/client/" + clientMrId + "/folio"))
                .andExpect(status().isUnauthorized());
    }
}

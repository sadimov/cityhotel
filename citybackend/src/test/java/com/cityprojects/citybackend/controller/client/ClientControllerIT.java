package com.cityprojects.citybackend.controller.client;

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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test d'integration Failsafe : valide la chaine complete
 * JWT &rarr; {@code @PreAuthorize} &rarr; {@code @TenantId} &rarr; DB sur le
 * controller client (Tour 10.1).
 *
 * <h3>Stack</h3>
 * <p>MockMvc + JWT factice signe via {@link JwtTokenProvider} (meme cle que
 * la prod, profil "test"). H2 in-memory en mode PostgreSQL via le profil
 * "test" (Liquibase desactive : on insere les hotels, roles et users en DDL
 * directement). Pas de Testcontainers ici — l'objectif est de valider la
 * chaine HTTP, pas la concurrence Postgres.</p>
 *
 * <h3>Pourquoi setup explicite des roles et users en DB</h3>
 * <p>{@link com.cityprojects.citybackend.security.JwtAuthenticationFilter}
 * appelle {@code CustomUserDetailsService.loadUserById(userId)} qui charge
 * l'entite {@link DBUser} depuis la base : sans seed, le filtre logge en
 * ERROR mais laisse passer la chaine SANS authentification, et on tombe en
 * 401 sur le {@code @PreAuthorize} au lieu du 201/403 attendu.</p>
 *
 * <h3>Pas de @Transactional sur les tests</h3>
 * <p>Comme {@link com.cityprojects.citybackend.common.tenant.TenantMultiTenancyIT}
 * et {@link com.cityprojects.citybackend.service.client.ClientServiceTests} :
 * un {@code @Transactional} sur la classe rollback les INSERT et casserait le
 * load via repository depuis MockMvc (les requetes HTTP ouvrent leur propre
 * transaction). Cleanup explicite en {@link #setUp()} / {@link #tearDown()}.</p>
 *
 * <h3>Pourquoi MockMvcBuilders.webAppContextSetup et pas @AutoConfigureMockMvc</h3>
 * <p>On veut explicitement appliquer les filtres Spring Security
 * (chaine JWT + {@code AuthorizationFilter}) via {@code apply(springSecurity())},
 * ce qui rend le branchement Auth/PreAuthorize identique a la prod.
 * {@code @AutoConfigureMockMvc} l'aurait fait aussi, mais le builder explicite
 * documente l'intention.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class ClientControllerIT {

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

    private DBUser userGerantHotel1;
    private DBUser userMagasinHotel1;
    private DBUser userGerantHotel2;

    private Long hotelMrId;
    private Long hotelFrId;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        transactionTemplate = new TransactionTemplate(transactionManager);

        // Cleanup ordonne (FK : clients -> societes/hotels, dbusers -> hotels/roles)
        jdbcTemplate.update("DELETE FROM client.clients");
        jdbcTemplate.update("DELETE FROM client.societes");
        jdbcTemplate.update("DELETE FROM finance.numerotation_sequence");
        jdbcTemplate.update("DELETE FROM core.dbusers");
        jdbcTemplate.update("DELETE FROM core.hotels");
        jdbcTemplate.update("DELETE FROM core.roles");

        // 2 hotels (Liquibase desactive en test : on les insere via repo JPA)
        Hotel mr = new Hotel("MR1", "Hotel Mauritanie");
        mr.setCodePays("MR");
        hotelMrId = hotelRepository.saveAndFlush(mr).getHotelId();

        Hotel fr = new Hotel("FR1", "Hotel France");
        fr.setCodePays("FR");
        hotelFrId = hotelRepository.saveAndFlush(fr).getHotelId();

        // Roles minimum requis (insert par roleCode utilises dans les tests)
        Role gerant = roleRepository.saveAndFlush(new Role("GERANT", "Gerant"));
        Role magasin = roleRepository.saveAndFlush(new Role("MAGASIN", "Magasin"));

        // Users : 1 gerant + 1 magasin sur hotel MR, 1 gerant sur hotel FR
        userGerantHotel1 = userRepository.saveAndFlush(buildUser(
                "gerant1", "gerant1@h1.test", "Karim", "Sow", mr, gerant));
        userMagasinHotel1 = userRepository.saveAndFlush(buildUser(
                "magasin1", "magasin1@h1.test", "Sidi", "Cheikh", mr, magasin));
        userGerantHotel2 = userRepository.saveAndFlush(buildUser(
                "gerant2", "gerant2@h2.test", "Pierre", "Dupont", fr, gerant));

        // MockMvc avec la chaine de filtres Spring Security branchee
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        jdbcTemplate.update("DELETE FROM client.clients");
        jdbcTemplate.update("DELETE FROM client.societes");
        jdbcTemplate.update("DELETE FROM finance.numerotation_sequence");
        jdbcTemplate.update("DELETE FROM core.dbusers");
        jdbcTemplate.update("DELETE FROM core.hotels");
        jdbcTemplate.update("DELETE FROM core.roles");
    }

    /**
     * Genere un JWT valide pour un user (chaine identique a celle de l'auth login :
     * UserPrincipal -> generateTokenForUser).
     */
    private String jwtFor(DBUser user) {
        UserPrincipal principal = UserPrincipal.create(user, Collections.singletonList(
                new SimpleGrantedAuthority("ROLE_" + user.getRole().getRoleCode())));
        return jwtTokenProvider.generateTokenForUser(principal);
    }

    /**
     * Helper : cree un client via JPA (hors HTTP) dans le tenant indique pour
     * preparer un cas cross-tenant. Utilise {@link TenantContext} le temps de
     * la transaction d'insert pour que Hibernate populate {@code hotel_id} via
     * le resolver.
     */
    private Long createClientInTenant(Long hotelId, String prenom, String nom, String numeroClient) {
        try {
            TenantContext.set(hotelId);
            return transactionTemplate.execute(s -> {
                Client c = new Client();
                c.setPrenom(prenom);
                c.setNom(nom);
                c.setNumeroClient(numeroClient);
                c.setActif(Boolean.TRUE);
                return clientRepository.save(c).getClientId();
            });
        } finally {
            TenantContext.clear();
        }
    }

    private static DBUser buildUser(String username, String email, String prenom,
                                    String nom, Hotel hotel, Role role) {
        // BCrypt placeholder : on n'authentifie jamais par mot de passe dans ces tests,
        // seulement par JWT signe (signature verifiee via la cle partagee).
        DBUser user = new DBUser(username, email, "$2a$12$placeholderhashplaceholderhashplaceholderhashplacehash",
                prenom, nom, hotel, role);
        user.setActif(Boolean.TRUE);
        user.setCompteVerrouille(Boolean.FALSE);
        return user;
    }

    @Test
    @DisplayName("T1 - GERANT hotel MR cree un client : 201, format CLI-{annee}-MR-{seq}, pas de hotelId expose")
    void shouldCreateClientWhenGerant() throws Exception {
        String jwt = jwtFor(userGerantHotel1);
        // UTF-8 explicite : "Sidibe" + accent doit serialiser proprement (Africa/Nouakchott).
        String body = "{"
                + "\"prenom\":\"Mohamed\","
                + "\"nom\":\"Sidibe\","
                + "\"email\":\"msidibe@example.mr\","
                + "\"telephone\":\"+22245678901\""
                + "}";

        mockMvc.perform(post("/api/clients")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(body.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.clientId").exists())
                .andExpect(jsonPath("$.numeroClient", matchesPattern("CLI-\\d{4}-MR-\\d{6}")))
                .andExpect(jsonPath("$.nom").value("Sidibe"))
                .andExpect(jsonPath("$.prenom").value("Mohamed"))
                // Le DTO de sortie ne doit JAMAIS exposer hotelId (Tour 9 multitenant-guardian).
                .andExpect(jsonPath("$.hotelId").doesNotExist());
    }

    @Test
    @DisplayName("T2 - MAGASIN tente GET /api/clients : 403 (matrice @PreAuthorize n'inclut pas MAGASIN en lecture client)")
    void shouldDenyAccessForMagasin() throws Exception {
        String jwt = jwtFor(userMagasinHotel1);

        mockMvc.perform(get("/api/clients")
                        .param("q", "test")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("T3 - JWT hotel FR lit client de hotel MR : 404 (Hibernate filtre via @TenantId)")
    void shouldReturn404ForCrossTenantRead() throws Exception {
        // Cree un client dans hotel MR via JPA direct (pas via HTTP).
        Long mrClientId = createClientInTenant(hotelMrId, "Ahmed", "Sow",
                "CLI-2026-MR-999999");

        // Tente de le lire avec un JWT du hotel FR : Hibernate ajoute
        // WHERE hotel_id = <FR>, le repository retourne empty, le service jette
        // ResourceNotFoundException("error.client.notFound") -> 404.
        String jwt = jwtFor(userGerantHotel2);

        mockMvc.perform(get("/api/clients/" + mrClientId)
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isNotFound());
    }
}

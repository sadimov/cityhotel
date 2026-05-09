package com.cityprojects.citybackend.controller.restaurant;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.entity.core.DBUser;
import com.cityprojects.citybackend.entity.core.Hotel;
import com.cityprojects.citybackend.entity.core.Role;
import com.cityprojects.citybackend.entity.restaurant.ArticleMenu;
import com.cityprojects.citybackend.entity.restaurant.CategorieMenu;
import com.cityprojects.citybackend.entity.restaurant.Commande;
import com.cityprojects.citybackend.entity.restaurant.LigneCommande;
import com.cityprojects.citybackend.entity.restaurant.ModeReglementCommande;
import com.cityprojects.citybackend.entity.restaurant.StatutArticle;
import com.cityprojects.citybackend.entity.restaurant.StatutCommande;
import com.cityprojects.citybackend.repository.core.DBUserRepository;
import com.cityprojects.citybackend.repository.core.HotelRepository;
import com.cityprojects.citybackend.repository.core.RoleRepository;
import com.cityprojects.citybackend.repository.restaurant.ArticleMenuRepository;
import com.cityprojects.citybackend.repository.restaurant.CategorieMenuRepository;
import com.cityprojects.citybackend.repository.restaurant.CommandeRepository;
import com.cityprojects.citybackend.repository.restaurant.LigneCommandeRepository;
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
import java.time.Instant;
import java.util.Collections;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test d'integration Failsafe (Tour 24) du {@link CommandeController}.
 *
 * <h3>Cas couverts</h3>
 * <ol>
 *   <li>T1 : RESTAURANT cree une commande COMPTANT -&gt; 201, statut BROUILLON,
 *       pas de hotelId expose, numero COMM-{annee}-MR-000001.</li>
 *   <li>T2 : MAGASIN tente POST -&gt; 403 (pas de role mutation).</li>
 *   <li>T3 : JWT hotel FR lit commande hotel MR -&gt; 404 (Hibernate filtre
 *       via @TenantId).</li>
 * </ol>
 */
@SpringBootTest
@ActiveProfiles("test")
class CommandeControllerIT {

    @Autowired private WebApplicationContext webApplicationContext;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private DBUserRepository userRepository;
    @Autowired private HotelRepository hotelRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private CategorieMenuRepository categorieRepository;
    @Autowired private ArticleMenuRepository articleRepository;
    @Autowired private CommandeRepository commandeRepository;
    @Autowired private LigneCommandeRepository ligneCommandeRepository;

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;

    private MockMvc mockMvc;
    private TransactionTemplate tx;

    private DBUser userRestaurantHotel1;
    private DBUser userMagasinHotel1;
    private DBUser userGerantHotel2;

    private Long hotelMrId;
    private Long hotelFrId;
    private Long articleMrId;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        tx = new TransactionTemplate(transactionManager);
        cleanAll();

        Hotel mr = new Hotel("MR1", "Hotel Mauritanie");
        mr.setCodePays("MR");
        hotelMrId = hotelRepository.saveAndFlush(mr).getHotelId();

        Hotel fr = new Hotel("FR1", "Hotel France");
        fr.setCodePays("FR");
        hotelFrId = hotelRepository.saveAndFlush(fr).getHotelId();

        Role restaurant = roleRepository.saveAndFlush(new Role("RESTAURANT", "Restaurant"));
        Role magasin = roleRepository.saveAndFlush(new Role("MAGASIN", "Magasin"));
        Role gerant = roleRepository.saveAndFlush(new Role("GERANT", "Gerant"));

        userRestaurantHotel1 = userRepository.saveAndFlush(buildUser(
                "rest1", "rest1@mr.test", "Sidi", "Restaurant", mr, restaurant));
        userMagasinHotel1 = userRepository.saveAndFlush(buildUser(
                "mag1", "mag1@mr.test", "Karim", "Magasin", mr, magasin));
        userGerantHotel2 = userRepository.saveAndFlush(buildUser(
                "gerant2", "gerant2@fr.test", "Pierre", "Gerant", fr, gerant));

        // Catalogue MR : 1 categorie + 1 article actif
        try {
            TenantContext.set(hotelMrId);
            CategorieMenu cat = tx.execute(s -> {
                CategorieMenu c = new CategorieMenu();
                c.setNom("Plats");
                c.setOrdre(0);
                c.setActif(Boolean.TRUE);
                return categorieRepository.save(c);
            });
            ArticleMenu art = tx.execute(s -> {
                ArticleMenu a = new ArticleMenu();
                a.setCodeArticle("PLT1");
                a.setNom("Riz au poisson");
                a.setCategorieId(cat.getCategorieId());
                a.setPrix(BigDecimal.valueOf(1500));
                a.setActif(Boolean.TRUE);
                a.setDisponible(Boolean.TRUE);
                a.setStatut(StatutArticle.ACTIF);
                return articleRepository.save(a);
            });
            articleMrId = art.getArticleId();
        } finally {
            TenantContext.clear();
        }

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
        jdbcTemplate.update("DELETE FROM finance.numerotation_sequence");
        jdbcTemplate.update("DELETE FROM restaurant.tickets");
        jdbcTemplate.update("DELETE FROM restaurant.lignes_commande");
        jdbcTemplate.update("DELETE FROM restaurant.commandes");
        jdbcTemplate.update("DELETE FROM restaurant.articles_menus");
        jdbcTemplate.update("DELETE FROM restaurant.categories_menus");
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
     * Cree directement (JPA) une commande dans le tenant donne, sans passer par
     * l'API (pour T3 cross-tenant : on injecte cote MR puis on tente la lecture
     * cote FR).
     */
    private Long createCommandeDirectInTenant(Long hotelId, Long articleId) {
        try {
            TenantContext.set(hotelId);
            return tx.execute(s -> {
                Commande c = new Commande();
                c.setNumeroCommande("COMM-2026-MR-999999");
                c.setModeReglement(ModeReglementCommande.COMPTANT);
                c.setStatut(StatutCommande.BROUILLON);
                c.setDevise("MRU");
                c.setDateCommande(Instant.now());
                c.setMontantHt(BigDecimal.valueOf(1500));
                c.setMontantTtc(BigDecimal.valueOf(1500));
                Commande saved = commandeRepository.save(c);
                LigneCommande l = new LigneCommande();
                l.setCommandeId(saved.getCommandeId());
                l.setArticleId(articleId);
                l.setLibelle("Riz au poisson");
                l.setQuantite(BigDecimal.ONE);
                l.setPrixUnitaire(BigDecimal.valueOf(1500));
                ligneCommandeRepository.save(l);
                return saved.getCommandeId();
            });
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("T1 - RESTAURANT cree une commande COMPTANT : 201, statut BROUILLON, pas de hotelId expose")
    void shouldCreateCommandeWhenRestaurant() throws Exception {
        String jwt = jwtFor(userRestaurantHotel1);
        String body = "{"
                + "\"modeReglement\":\"COMPTANT\","
                + "\"devise\":\"MRU\","
                + "\"lignes\":[{"
                + "  \"articleId\":" + articleMrId + ","
                + "  \"quantite\":2"
                + "}]"
                + "}";

        mockMvc.perform(post("/api/restaurant/commandes")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(body.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.commandeId").exists())
                .andExpect(jsonPath("$.numeroCommande").value(
                        org.hamcrest.Matchers.matchesPattern("COMM-\\d{4}-MR-000001")))
                .andExpect(jsonPath("$.statut").value("BROUILLON"))
                .andExpect(jsonPath("$.modeReglement").value("COMPTANT"))
                .andExpect(jsonPath("$.montantTtc").value(3000.00))
                .andExpect(jsonPath("$.lignes.length()").value(1))
                .andExpect(jsonPath("$.lignes[0].libelle").value("Riz au poisson"))
                // Pas de hotelId expose
                .andExpect(jsonPath("$.hotelId").doesNotExist());
    }

    @Test
    @DisplayName("T2 - MAGASIN tente POST /api/restaurant/commandes : 403 (n'a pas le role mutation)")
    void shouldDenyAccessForMagasin() throws Exception {
        String jwt = jwtFor(userMagasinHotel1);
        String body = "{"
                + "\"modeReglement\":\"COMPTANT\","
                + "\"lignes\":[{\"articleId\":" + articleMrId + ",\"quantite\":1}]"
                + "}";

        mockMvc.perform(post("/api/restaurant/commandes")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("T3 - JWT hotel FR lit commande de hotel MR : 404 (Hibernate filtre via @TenantId)")
    void shouldReturn404ForCrossTenantRead() throws Exception {
        Long mrCommandeId = createCommandeDirectInTenant(hotelMrId, articleMrId);

        String jwt = jwtFor(userGerantHotel2);

        mockMvc.perform(get("/api/restaurant/commandes/" + mrCommandeId)
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isNotFound());
    }
}

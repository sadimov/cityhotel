package com.cityprojects.citybackend.controller.restaurant;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.entity.core.DBUser;
import com.cityprojects.citybackend.entity.core.Hotel;
import com.cityprojects.citybackend.entity.core.Role;
import com.cityprojects.citybackend.entity.restaurant.ArticleMenu;
import com.cityprojects.citybackend.entity.restaurant.CategorieMenu;
import com.cityprojects.citybackend.entity.restaurant.StatutArticle;
import com.cityprojects.citybackend.repository.core.DBUserRepository;
import com.cityprojects.citybackend.repository.core.HotelRepository;
import com.cityprojects.citybackend.repository.core.RoleRepository;
import com.cityprojects.citybackend.repository.restaurant.ArticleMenuRepository;
import com.cityprojects.citybackend.repository.restaurant.CategorieMenuRepository;
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
import java.util.Collections;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test d'integration Failsafe (Tour 23) : valide la chaine complete
 * JWT &rarr; {@code @PreAuthorize} &rarr; {@code @TenantId} &rarr; DB sur le
 * controller article menu.
 *
 * <h3>Cas couverts</h3>
 * <ol>
 *   <li>T1 : GERANT cree un article -&gt; 201, pas de hotelId expose, statut=ACTIF.</li>
 *   <li>T2 : RECEPTION tente POST -&gt; 403 (matrice {@code @PreAuthorize}
 *       n'inclut pas RECEPTION en mutation).</li>
 *   <li>T3 : JWT hotel FR lit article hotel MR -&gt; 404 (Hibernate filtre via
 *       {@code @TenantId}).</li>
 * </ol>
 */
@SpringBootTest
@ActiveProfiles("test")
class ArticleMenuControllerIT {

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
    private CategorieMenuRepository categorieRepository;

    @Autowired
    private ArticleMenuRepository articleRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private MockMvc mockMvc;
    private TransactionTemplate transactionTemplate;

    private DBUser userGerantHotel1;
    private DBUser userReceptionHotel1;
    private DBUser userGerantHotel2;

    private Long hotelMrId;
    private Long hotelFrId;
    private Long categorieMrId;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        transactionTemplate = new TransactionTemplate(transactionManager);

        // Cleanup ordonne (FK : articles -> categories -> hotels)
        jdbcTemplate.update("DELETE FROM restaurant.articles_menus");
        jdbcTemplate.update("DELETE FROM restaurant.categories_menus");
        jdbcTemplate.update("DELETE FROM core.dbusers");
        jdbcTemplate.update("DELETE FROM core.hotels");
        jdbcTemplate.update("DELETE FROM core.roles");

        // 2 hotels
        Hotel mr = new Hotel("MR1", "Hotel Mauritanie");
        mr.setCodePays("MR");
        hotelMrId = hotelRepository.saveAndFlush(mr).getHotelId();

        Hotel fr = new Hotel("FR1", "Hotel France");
        fr.setCodePays("FR");
        hotelFrId = hotelRepository.saveAndFlush(fr).getHotelId();

        // Roles
        Role gerant = roleRepository.saveAndFlush(new Role("GERANT", "Gerant"));
        Role reception = roleRepository.saveAndFlush(new Role("RECEPTION", "Reception"));

        // Users
        userGerantHotel1 = userRepository.saveAndFlush(buildUser(
                "gerant1", "gerant1@h1.test", "Sidi", "Cheikh", mr, gerant));
        userReceptionHotel1 = userRepository.saveAndFlush(buildUser(
                "reception1", "reception1@h1.test", "Karim", "Sow", mr, reception));
        userGerantHotel2 = userRepository.saveAndFlush(buildUser(
                "gerant2", "gerant2@h2.test", "Pierre", "Dupont", fr, gerant));

        // Categorie dans hotel MR (seed via JPA avec TenantContext.set)
        try {
            TenantContext.set(hotelMrId);
            categorieMrId = transactionTemplate.execute(s -> {
                CategorieMenu c = new CategorieMenu();
                c.setNom("Plats");
                c.setOrdre(0);
                c.setActif(Boolean.TRUE);
                return categorieRepository.save(c).getCategorieId();
            });
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
        DBUser user = new DBUser(username, email, "$2a$12$placeholderhashplaceholderhashplaceholderhashplacehash",
                prenom, nom, hotel, role);
        user.setActif(Boolean.TRUE);
        user.setCompteVerrouille(Boolean.FALSE);
        return user;
    }

    private Long createArticleInTenant(Long hotelId, Long categorieId, String code, String nom) {
        try {
            TenantContext.set(hotelId);
            return transactionTemplate.execute(s -> {
                ArticleMenu a = new ArticleMenu();
                a.setCodeArticle(code);
                a.setNom(nom);
                a.setCategorieId(categorieId);
                a.setPrix(BigDecimal.valueOf(100));
                a.setActif(Boolean.TRUE);
                a.setDisponible(Boolean.TRUE);
                a.setStatut(StatutArticle.ACTIF);
                return articleRepository.save(a).getArticleId();
            });
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("T1 - GERANT hotel MR cree un article : 201, statut=ACTIF, pas de hotelId expose")
    void shouldCreateArticleWhenGerant() throws Exception {
        String jwt = jwtFor(userGerantHotel1);
        String body = "{"
                + "\"codeArticle\":\"PLT1\","
                + "\"nom\":\"Riz au poisson\","
                + "\"categorieId\":" + categorieMrId + ","
                + "\"prix\":150.00"
                + "}";

        mockMvc.perform(post("/api/restaurant/articles")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(body.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.articleId").exists())
                .andExpect(jsonPath("$.codeArticle").value("PLT1"))
                .andExpect(jsonPath("$.nom").value("Riz au poisson"))
                .andExpect(jsonPath("$.statut").value("ACTIF"))
                // Pas de hotelId expose dans le DTO
                .andExpect(jsonPath("$.hotelId").doesNotExist());
    }

    @Test
    @DisplayName("T2 - RECEPTION tente POST /api/restaurant/articles : 403 (n'a pas le role mutation)")
    void shouldDenyAccessForReception() throws Exception {
        String jwt = jwtFor(userReceptionHotel1);
        String body = "{"
                + "\"codeArticle\":\"X1\","
                + "\"nom\":\"X\","
                + "\"categorieId\":" + categorieMrId + ","
                + "\"prix\":10.00"
                + "}";

        mockMvc.perform(post("/api/restaurant/articles")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("T3 - JWT hotel FR lit article de hotel MR : 404 (Hibernate filtre via @TenantId)")
    void shouldReturn404ForCrossTenantRead() throws Exception {
        // Cree un article dans hotel MR via JPA direct
        Long mrArticleId = createArticleInTenant(hotelMrId, categorieMrId, "MR-PLT1", "Article MR");

        // Tente de le lire avec un JWT du hotel FR
        String jwt = jwtFor(userGerantHotel2);

        mockMvc.perform(get("/api/restaurant/articles/" + mrArticleId)
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isNotFound());
    }
}

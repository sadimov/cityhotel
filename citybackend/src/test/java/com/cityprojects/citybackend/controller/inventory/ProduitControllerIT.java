package com.cityprojects.citybackend.controller.inventory;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.entity.core.DBUser;
import com.cityprojects.citybackend.entity.core.Hotel;
import com.cityprojects.citybackend.entity.core.Role;
import com.cityprojects.citybackend.entity.inventory.CategorieProduit;
import com.cityprojects.citybackend.entity.inventory.Produit;
import com.cityprojects.citybackend.repository.core.DBUserRepository;
import com.cityprojects.citybackend.repository.core.HotelRepository;
import com.cityprojects.citybackend.repository.core.RoleRepository;
import com.cityprojects.citybackend.repository.inventory.CategorieProduitRepository;
import com.cityprojects.citybackend.repository.inventory.ProduitRepository;
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
 * Test d'integration Failsafe : valide la chaine complete
 * JWT &rarr; {@code @PreAuthorize} &rarr; {@code @TenantId} &rarr; DB sur le
 * controller produit (Tour 16).
 *
 * <h3>Cas couverts</h3>
 * <ol>
 *   <li>T1 : MAGASIN cree un produit -&gt; 201, pas de hotelId expose, stockActuel=0.</li>
 *   <li>T2 : RECEPTION (autre role) tente POST -&gt; 403 (matrice {@code @PreAuthorize}
 *       n'inclut pas RECEPTION en inventory).</li>
 *   <li>T3 : JWT hotel FR lit produit hotel MR -&gt; 404 (Hibernate filtre via
 *       {@code @TenantId}).</li>
 * </ol>
 */
@SpringBootTest
@ActiveProfiles("test")
class ProduitControllerIT {

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
    private CategorieProduitRepository categorieRepository;

    @Autowired
    private ProduitRepository produitRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private MockMvc mockMvc;
    private TransactionTemplate transactionTemplate;

    private DBUser userMagasinHotel1;
    private DBUser userReceptionHotel1;
    private DBUser userMagasinHotel2;

    private Long hotelMrId;
    private Long hotelFrId;
    private Long categorieMrId;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        transactionTemplate = new TransactionTemplate(transactionManager);

        // Cleanup ordonne
        jdbcTemplate.update("DELETE FROM inventory.mouvements_stock");
        jdbcTemplate.update("DELETE FROM inventory.lignes_bons_commande");
        jdbcTemplate.update("DELETE FROM inventory.bons_commande");
        jdbcTemplate.update("DELETE FROM inventory.lignes_bons_sortie");
        jdbcTemplate.update("DELETE FROM inventory.bons_sortie");
        jdbcTemplate.update("DELETE FROM inventory.produits");
        jdbcTemplate.update("DELETE FROM inventory.categories_produits");
        jdbcTemplate.update("DELETE FROM inventory.fournisseurs");
        jdbcTemplate.update("DELETE FROM finance.numerotation_sequence");
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
        Role magasin = roleRepository.saveAndFlush(new Role("MAGASIN", "Magasin"));
        Role reception = roleRepository.saveAndFlush(new Role("RECEPTION", "Reception"));

        // Users
        userMagasinHotel1 = userRepository.saveAndFlush(buildUser(
                "magasin1", "magasin1@h1.test", "Sidi", "Cheikh", mr, magasin));
        userReceptionHotel1 = userRepository.saveAndFlush(buildUser(
                "reception1", "reception1@h1.test", "Karim", "Sow", mr, reception));
        userMagasinHotel2 = userRepository.saveAndFlush(buildUser(
                "magasin2", "magasin2@h2.test", "Pierre", "Dupont", fr, magasin));

        // Categorie dans hotel MR (seed via JPA avec TenantContext.set)
        try {
            TenantContext.set(hotelMrId);
            categorieMrId = transactionTemplate.execute(s -> {
                CategorieProduit c = new CategorieProduit();
                c.setCodeCategorie("FB");
                c.setNomCategorie("Food & Beverage");
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
        jdbcTemplate.update("DELETE FROM inventory.mouvements_stock");
        jdbcTemplate.update("DELETE FROM inventory.produits");
        jdbcTemplate.update("DELETE FROM inventory.categories_produits");
        jdbcTemplate.update("DELETE FROM inventory.fournisseurs");
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

    private Long createProduitInTenant(Long hotelId, Long categorieId, String code, String nom) {
        try {
            TenantContext.set(hotelId);
            return transactionTemplate.execute(s -> {
                Produit p = new Produit();
                p.setCodeProduit(code);
                p.setNomProduit(nom);
                p.setCategorieId(categorieId);
                p.setUniteMesure("kg");
                p.setActif(Boolean.TRUE);
                p.setStockActuel(0);
                return produitRepository.save(p).getProduitId();
            });
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("T1 - MAGASIN hotel MR cree un produit : 201, stockActuel=0, pas de hotelId expose")
    void shouldCreateProduitWhenMagasin() throws Exception {
        String jwt = jwtFor(userMagasinHotel1);
        String body = "{"
                + "\"codeProduit\":\"EAU500\","
                + "\"nomProduit\":\"Eau minerale 500ml\","
                + "\"categorieId\":" + categorieMrId + ","
                + "\"uniteMesure\":\"bouteille\","
                + "\"prixUnitaire\":20.00,"
                + "\"seuilAlerte\":10,"
                + "\"seuilCritique\":5"
                + "}";

        mockMvc.perform(post("/api/inventory/produits")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(body.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.produitId").exists())
                .andExpect(jsonPath("$.codeProduit").value("EAU500"))
                .andExpect(jsonPath("$.nomProduit").value("Eau minerale 500ml"))
                .andExpect(jsonPath("$.stockActuel").value(0))
                // Pas de hotelId expose dans le DTO
                .andExpect(jsonPath("$.hotelId").doesNotExist());
    }

    @Test
    @DisplayName("T2 - RECEPTION tente POST /api/inventory/produits : 403 (n'a pas le role MAGASIN/GERANT/...)")
    void shouldDenyAccessForReception() throws Exception {
        String jwt = jwtFor(userReceptionHotel1);
        String body = "{"
                + "\"codeProduit\":\"X1\","
                + "\"nomProduit\":\"X\","
                + "\"categorieId\":" + categorieMrId + ","
                + "\"uniteMesure\":\"u\""
                + "}";

        mockMvc.perform(post("/api/inventory/produits")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("T3 - JWT hotel FR lit produit de hotel MR : 404 (Hibernate filtre via @TenantId)")
    void shouldReturn404ForCrossTenantRead() throws Exception {
        // Cree un produit dans hotel MR via JPA direct
        Long mrProduitId = createProduitInTenant(hotelMrId, categorieMrId, "MR-PRD1", "Produit MR");

        // Tente de le lire avec un JWT du hotel FR
        String jwt = jwtFor(userMagasinHotel2);

        mockMvc.perform(get("/api/inventory/produits/" + mrProduitId)
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isNotFound());
    }
}

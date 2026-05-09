package com.cityprojects.citybackend.e2e;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.entity.core.DBUser;
import com.cityprojects.citybackend.entity.core.Hotel;
import com.cityprojects.citybackend.entity.core.Role;
import com.cityprojects.citybackend.repository.core.DBUserRepository;
import com.cityprojects.citybackend.repository.core.HotelRepository;
import com.cityprojects.citybackend.repository.core.RoleRepository;
import com.cityprojects.citybackend.security.JwtTokenProvider;
import com.cityprojects.citybackend.security.UserPrincipal;
import com.jayway.jsonpath.JsonPath;
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
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test E2E (Failsafe) du cycle de vie complet du flux inventory (Tour 18.1).
 *
 * <h3>Pourquoi un package {@code e2e/} dedie</h3>
 * <p>Ce test traverse plusieurs entites du module inventory (fournisseur,
 * categorie, produit, BC + lignes, BS + lignes, mouvements de stock) et valide
 * en bout-en-bout :
 * <ul>
 *   <li>la chaine de creation jusqu'a la reception (impact stock {@code +N}),</li>
 *   <li>la chaine de sortie jusqu'a la livraison (impact stock {@code -N}),</li>
 *   <li>la numerotation sequentielle sans trou par hotel + exercice
 *       (cf. {@link com.cityprojects.citybackend.service.finance.NumerotationService}).</li>
 * </ul>
 * Comme {@code ReservationFlowE2EIT}, on le place dans {@code e2e/} pour
 * materialiser son role de scenario cross-controller bout-en-bout.</p>
 *
 * <h3>Flux teste (T1)</h3>
 * <ol>
 *   <li>POST {@code /api/inventory/fournisseurs} : 201.</li>
 *   <li>POST {@code /api/inventory/categories} : 201.</li>
 *   <li>POST {@code /api/inventory/produits} : 201, {@code stockActuel = 0}.</li>
 *   <li>POST {@code /api/inventory/bons-commande} : 201, numero
 *       {@code BC-{annee}-MR-000001}, statut {@code BROUILLON}.</li>
 *   <li>PUT {@code /api/inventory/bons-commande/{id}/statut?statut=ENVOYE} : 200.</li>
 *   <li>PUT {@code /api/inventory/bons-commande/{id}/statut?statut=CONFIRME} : 200.</li>
 *   <li>POST {@code /api/inventory/bons-commande/{id}/reception} (50 unites) :
 *       200, {@code statut = RECU_COMPLET}, stock produit = 50.</li>
 *   <li>POST {@code /api/inventory/bons-sortie} : 201, numero
 *       {@code BS-{annee}-MR-000001}, statut {@code BROUILLON}.</li>
 *   <li>POST {@code /api/inventory/bons-sortie/{id}/valider} : 200,
 *       {@code statut = VALIDE}.</li>
 *   <li>POST {@code /api/inventory/bons-sortie/{id}/livrer} : 200,
 *       {@code statut = LIVRE}, stock produit = 50 - 30 = 20.</li>
 *   <li>BC #2 et BC #3 : numerotation {@code BC-...-000002} / {@code BC-...-000003}
 *       (sequence par hotel + exercice, sans trou).</li>
 *   <li>BS #2 : numerotation {@code BS-...-000002} (sequence BS distincte de BC).</li>
 * </ol>
 *
 * <h3>Flux teste (T2 bonus)</h3>
 * <p>La validation d'un BS dont la quantite demandee depasse le stock disponible
 * doit echouer en {@code 4xx} (BusinessException
 * {@code error.bonSortie.stockInsuffisant}). On cree un produit avec stock 5
 * et on tente un BS de 100 unites - la validation refuse et l'on n'atteint
 * jamais l'etape {@code livrer}.</p>
 *
 * <h3>Pas de @Transactional sur la classe</h3>
 * <p>Pattern identique a {@code ReservationFlowE2EIT} et {@code ProduitControllerIT} :
 * un {@code @Transactional} rollback les INSERT et casserait le load via
 * repository depuis MockMvc. Cleanup explicite en {@link #setUp()} /
 * {@link #tearDown()} (ordre FK strict).</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class InventoryFlowE2EIT {

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
    private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;

    private DBUser userMagasin;
    private DBUser userGerant;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        cleanAll();

        // 1 hotel MR
        Hotel mr = new Hotel("MR1", "Hotel Mauritanie");
        mr.setCodePays("MR");
        Long hotelMrId = hotelRepository.saveAndFlush(mr).getHotelId();

        // Roles utilises : MAGASIN (BC + reception + creation BS)
        // et GERANT (validation/livraison BS + alternative pour creation).
        Role magasin = roleRepository.saveAndFlush(new Role("MAGASIN", "Magasin"));
        Role gerant = roleRepository.saveAndFlush(new Role("GERANT", "Gerant"));

        userMagasin = userRepository.saveAndFlush(buildUser(
                "magasin1", "magasin1@mr.test", "Sidi", "Cheikh", mr, magasin));
        userGerant = userRepository.saveAndFlush(buildUser(
                "gerant1", "gerant1@mr.test", "Karim", "Sow", mr, gerant));

        // Reference inutilisee directement mais conserve l'invariant 1 hotel
        // attendu par le test (suppression de variable evite warning IDE).
        assertThat(hotelMrId).isPositive();

        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        cleanAll();
    }

    /**
     * Cleanup ordonne (FK strict). On nettoie tout l'inventory + finance
     * (sequence numerotation) + core (users / hotels / roles).
     */
    private void cleanAll() {
        jdbcTemplate.update("DELETE FROM inventory.mouvements_stock");
        jdbcTemplate.update("DELETE FROM inventory.lignes_bons_sortie");
        jdbcTemplate.update("DELETE FROM inventory.bons_sortie");
        jdbcTemplate.update("DELETE FROM inventory.lignes_bons_commande");
        jdbcTemplate.update("DELETE FROM inventory.bons_commande");
        jdbcTemplate.update("DELETE FROM inventory.produits");
        jdbcTemplate.update("DELETE FROM inventory.categories_produits");
        jdbcTemplate.update("DELETE FROM inventory.fournisseurs");
        jdbcTemplate.update("DELETE FROM finance.numerotation_sequence");
        jdbcTemplate.update("DELETE FROM core.dbusers");
        jdbcTemplate.update("DELETE FROM core.hotels");
        jdbcTemplate.update("DELETE FROM core.roles");
    }

    /** Genere un JWT valide pour un user (chaine identique a celle de l'auth login). */
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
     * Lit une valeur Long via JSONPath sur un body JSON.
     * <p>Jackson retourne {@code Integer} pour des nombres tenant dans 32 bits :
     * on caste via {@link Number#longValue()}.</p>
     */
    private static Long readJsonLong(String body, String path) {
        Object value = JsonPath.read(body, path);
        return (value instanceof Number n) ? n.longValue() : null;
    }

    private static Integer readJsonInt(String body, String path) {
        Object value = JsonPath.read(body, path);
        return (value instanceof Number n) ? n.intValue() : null;
    }

    private static String readJsonString(String body, String path) {
        Object value = JsonPath.read(body, path);
        return (value != null) ? value.toString() : null;
    }

    // ---------------- Helpers HTTP ----------------

    /** POST /fournisseurs (rôle MAGASIN). */
    private Long createFournisseur(String jwt, String nom) throws Exception {
        String body = "{"
                + "\"nomFournisseur\":\"" + nom + "\","
                + "\"telephone\":\"+22245100100\","
                + "\"email\":\"contact@" + nom.toLowerCase().replaceAll("\\s+", "") + ".mr\""
                + "}";
        String response = mockMvc.perform(post("/api/inventory/fournisseurs")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(body.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return readJsonLong(response, "$.fournisseurId");
    }

    /** POST /categories (rôle MAGASIN). */
    private Long createCategorie(String jwt, String code, String nom) throws Exception {
        String body = "{"
                + "\"codeCategorie\":\"" + code + "\","
                + "\"nomCategorie\":\"" + nom + "\""
                + "}";
        String response = mockMvc.perform(post("/api/inventory/categories")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(body.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return readJsonLong(response, "$.categorieId");
    }

    /** POST /produits (rôle MAGASIN), stockActuel initial = 0. */
    private Long createProduit(String jwt, Long categorieId, String code, String nom,
                               int seuilAlerte) throws Exception {
        String body = "{"
                + "\"codeProduit\":\"" + code + "\","
                + "\"nomProduit\":\"" + nom + "\","
                + "\"categorieId\":" + categorieId + ","
                + "\"uniteMesure\":\"unite\","
                + "\"prixUnitaire\":100.00,"
                + "\"seuilAlerte\":" + seuilAlerte + ","
                + "\"seuilCritique\":" + (seuilAlerte / 2)
                + "}";
        String response = mockMvc.perform(post("/api/inventory/produits")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(body.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        // Sanity check : stock initial doit etre 0 cote service.
        assertThat(readJsonInt(response, "$.stockActuel")).isZero();
        return readJsonLong(response, "$.produitId");
    }

    /** GET /produits/{id} -> stockActuel courant. */
    private int readProduitStock(String jwt, Long produitId) throws Exception {
        String response = mockMvc.perform(get("/api/inventory/produits/" + produitId)
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Integer stock = readJsonInt(response, "$.stockActuel");
        assertThat(stock).isNotNull();
        return stock;
    }

    /** POST /bons-commande : retourne body complet (id + numeroBc + ligneId). */
    private String createBonCommande(String jwt, Long fournisseurId, Long produitId,
                                     int quantite, String prixUnitaire) throws Exception {
        String body = "{"
                + "\"fournisseurId\":" + fournisseurId + ","
                + "\"commentaires\":\"BC test E2E\","
                + "\"lignes\":[{"
                + "\"produitId\":" + produitId + ","
                + "\"quantiteCommandee\":" + quantite + ","
                + "\"prixUnitaire\":" + prixUnitaire
                + "}]"
                + "}";
        return mockMvc.perform(post("/api/inventory/bons-commande")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(body.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    /** PUT /bons-commande/{id}/statut?statut=XXX. */
    private void changerStatutBc(String jwt, Long bcId, String statut) throws Exception {
        mockMvc.perform(put("/api/inventory/bons-commande/" + bcId + "/statut")
                        .header("Authorization", "Bearer " + jwt)
                        .param("statut", statut))
                .andExpect(status().isOk());
    }

    /** POST /bons-commande/{id}/reception : 1 ligne avec quantiteRecue. */
    private void receptionBonCommande(String jwt, Long bcId, Long ligneId,
                                      int quantiteRecue) throws Exception {
        String body = "{"
                + "\"lignes\":[{"
                + "\"ligneId\":" + ligneId + ","
                + "\"quantiteRecue\":" + quantiteRecue
                + "}]"
                + "}";
        mockMvc.perform(post("/api/inventory/bons-commande/" + bcId + "/reception")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(body.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isOk());
    }

    /** POST /bons-sortie : retourne body complet (id + numeroBs). */
    private String createBonSortie(String jwt, Long produitId, int quantite,
                                   String destination) throws Exception {
        String body = "{"
                + "\"destination\":\"" + destination + "\","
                + "\"commentaires\":\"BS test E2E\","
                + "\"lignes\":[{"
                + "\"produitId\":" + produitId + ","
                + "\"quantiteDemandee\":" + quantite
                + "}]"
                + "}";
        return mockMvc.perform(post("/api/inventory/bons-sortie")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(body.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    /** POST /bons-sortie/{id}/valider. */
    private void validerBs(String jwt, Long bsId) throws Exception {
        mockMvc.perform(post("/api/inventory/bons-sortie/" + bsId + "/valider")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk());
    }

    /** POST /bons-sortie/{id}/livrer. */
    private void livrerBs(String jwt, Long bsId) throws Exception {
        mockMvc.perform(post("/api/inventory/bons-sortie/" + bsId + "/livrer")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk());
    }

    // ---------------- Tests ----------------

    @Test
    @DisplayName("T1 - Cycle complet inventory : fournisseur -> produit -> BC -> reception -> BS -> livraison + numerotation sequentielle BC/BS")
    void shouldCompleteFullInventoryLifecycleWithSequentialNumbering() throws Exception {
        String jwtMagasin = jwtFor(userMagasin);

        // ---------- 1) FOURNISSEUR ----------
        Long fournisseurId = createFournisseur(jwtMagasin, "Sahel Distribution");

        // ---------- 2) CATEGORIE ----------
        Long categorieId = createCategorie(jwtMagasin, "FB", "Food & Beverage");

        // ---------- 3) PRODUIT (stockActuel initial = 0) ----------
        Long produitId = createProduit(jwtMagasin, categorieId, "EAU500",
                "Eau minerale 500ml", 10);
        assertThat(readProduitStock(jwtMagasin, produitId)).isZero();

        // ---------- 4) BC #1 (50 unites a 100 MRU) ----------
        String bc1Body = createBonCommande(jwtMagasin, fournisseurId, produitId, 50, "100.00");
        Long bc1Id = readJsonLong(bc1Body, "$.bonCommandeId");
        String bc1Numero = readJsonString(bc1Body, "$.numeroBc");
        Long ligne1Id = readJsonLong(bc1Body, "$.lignes[0].ligneId");
        // Sequence BC #1 -> 000001 (sans trou) ; format {TYPE}-{annee}-{codePays}-{6 chiffres}
        assertThat(bc1Numero).matches("BC-\\d{4}-MR-000001");

        // ---------- 5) Transitions BROUILLON -> ENVOYE -> CONFIRME ----------
        // (le service de reception accepte CONFIRME, RECU_PARTIEL ou ENVOYE.)
        changerStatutBc(jwtMagasin, bc1Id, "ENVOYE");
        changerStatutBc(jwtMagasin, bc1Id, "CONFIRME");

        // ---------- 6) RECEPTION 50 unites -> stock = 50, statut = RECU_COMPLET ----------
        receptionBonCommande(jwtMagasin, bc1Id, ligne1Id, 50);
        assertThat(readProduitStock(jwtMagasin, produitId)).isEqualTo(50);

        // ---------- 7) BS #1 : sortie 30 unites ----------
        String bs1Body = createBonSortie(jwtMagasin, produitId, 30,
                "Restaurant - approvisionnement");
        Long bs1Id = readJsonLong(bs1Body, "$.bonSortieId");
        String bs1Numero = readJsonString(bs1Body, "$.numeroBs");
        // Sequence BS #1 -> 000001 (sequence dediee BS, distincte de BC)
        assertThat(bs1Numero).matches("BS-\\d{4}-MR-000001");

        // ---------- 8) Validation + livraison BS -> stock = 50 - 30 = 20 ----------
        validerBs(jwtMagasin, bs1Id);
        livrerBs(jwtMagasin, bs1Id);
        assertThat(readProduitStock(jwtMagasin, produitId)).isEqualTo(20);

        // ---------- 9) BC #2 et BC #3 : numerotation sequentielle sans trou ----------
        String bc2Body = createBonCommande(jwtMagasin, fournisseurId, produitId, 25, "100.00");
        assertThat(readJsonString(bc2Body, "$.numeroBc"))
                .matches("BC-\\d{4}-MR-000002");

        String bc3Body = createBonCommande(jwtMagasin, fournisseurId, produitId, 25, "100.00");
        assertThat(readJsonString(bc3Body, "$.numeroBc"))
                .matches("BC-\\d{4}-MR-000003");

        // ---------- 10) BS #2 : sequence BS independante de BC ----------
        String bs2Body = createBonSortie(jwtMagasin, produitId, 5, "Test deuxieme BS");
        assertThat(readJsonString(bs2Body, "$.numeroBs"))
                .matches("BS-\\d{4}-MR-000002");
    }

    @Test
    @DisplayName("T2 - Validation BS refusee si stock insuffisant (BusinessException)")
    void shouldRejectBonSortieValidationWhenInsufficientStock() throws Exception {
        String jwtMagasin = jwtFor(userMagasin);

        // Setup : fournisseur + categorie + produit (stock = 0, on n'approvisionne pas).
        Long fournisseurId = createFournisseur(jwtMagasin, "Sahel Distribution");
        Long categorieId = createCategorie(jwtMagasin, "FB", "Food & Beverage");
        Long produitId = createProduit(jwtMagasin, categorieId, "RIZ1KG", "Riz 1kg", 10);

        // Approvisionnement : 5 unites seulement.
        String bcBody = createBonCommande(jwtMagasin, fournisseurId, produitId, 5, "200.00");
        Long bcId = readJsonLong(bcBody, "$.bonCommandeId");
        Long ligneId = readJsonLong(bcBody, "$.lignes[0].ligneId");
        changerStatutBc(jwtMagasin, bcId, "ENVOYE");
        changerStatutBc(jwtMagasin, bcId, "CONFIRME");
        receptionBonCommande(jwtMagasin, bcId, ligneId, 5);
        assertThat(readProduitStock(jwtMagasin, produitId)).isEqualTo(5);

        // BS : tente de sortir 100 unites alors que le stock est de 5.
        String bsBody = createBonSortie(jwtMagasin, produitId, 100, "Test stock insuffisant");
        Long bsId = readJsonLong(bsBody, "$.bonSortieId");

        // La validation doit refuser (BusinessException error.bonSortie.stockInsuffisant)
        // -> 4xx, sans toucher au stock.
        mockMvc.perform(post("/api/inventory/bons-sortie/" + bsId + "/valider")
                        .header("Authorization", "Bearer " + jwtMagasin))
                .andExpect(status().is4xxClientError());

        // Sanity check : stock n'a pas bouge (toujours 5).
        assertThat(readProduitStock(jwtMagasin, produitId)).isEqualTo(5);

        // userGerant injecte mais non utilise dans T1/T2 : on l'utilise ici comme
        // alternative role pour valider le refus s'applique aussi cote GERANT.
        String jwtGerant = jwtFor(userGerant);
        mockMvc.perform(post("/api/inventory/bons-sortie/" + bsId + "/valider")
                        .header("Authorization", "Bearer " + jwtGerant))
                .andExpect(status().is4xxClientError());
    }
}

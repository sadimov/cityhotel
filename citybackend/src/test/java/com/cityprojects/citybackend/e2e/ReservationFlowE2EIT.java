package com.cityprojects.citybackend.e2e;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.entity.core.DBUser;
import com.cityprojects.citybackend.entity.core.Hotel;
import com.cityprojects.citybackend.entity.core.Role;
import com.cityprojects.citybackend.entity.hebergement.Chambre;
import com.cityprojects.citybackend.entity.hebergement.StatutChambre;
import com.cityprojects.citybackend.entity.hebergement.TypeChambre;
import com.cityprojects.citybackend.repository.core.DBUserRepository;
import com.cityprojects.citybackend.repository.core.HotelRepository;
import com.cityprojects.citybackend.repository.core.RoleRepository;
import com.cityprojects.citybackend.repository.hebergement.ChambreRepository;
import com.cityprojects.citybackend.repository.hebergement.TypeChambreRepository;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Collections;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test E2E (Failsafe) du cycle de vie complet d'une reservation (Tour 15.1).
 *
 * <h3>Pourquoi un package {@code e2e/} dedie</h3>
 * <p>Ce test traverse plusieurs modules (clients + hebergement) :
 * il n'a pas de "controller proprietaire" naturel et n'appartient pas a
 * {@code controller/client/} ni a {@code controller/hebergement/}. On le
 * place donc dans {@code e2e/} pour materialiser son role de scenario
 * cross-module bout-en-bout.</p>
 *
 * <h3>Flux teste (T1)</h3>
 * <ol>
 *   <li>POST {@code /api/clients} - creation client : 201, format
 *       {@code CLI-{annee}-MR-{seq}}.</li>
 *   <li>POST {@code /api/hebergement/reservations} : 201, statut {@code CONFIRMEE},
 *       format {@code RES-{annee}-MR-{seq}}, 1 nuit ({@code today} -> {@code today+1}).</li>
 *   <li>POST {@code /api/hebergement/reservations/{id}/check-in} : 200, statut
 *       {@code ARRIVEE}.</li>
 *   <li>GET {@code /api/hebergement/reservations/{id}/nuitees} : 1 nuitee, statut
 *       {@code CONSOMMEE} (la nuitee du jour est marquee CONSOMMEE par check-in
 *       car {@code !dateNuit.isAfter(today)}).</li>
 *   <li>POST {@code /api/hebergement/reservations/{id}/check-out} : 200, statut
 *       {@code PARTIE}.</li>
 *   <li>GET nuitees : toutes restent {@code CONSOMMEE}.</li>
 * </ol>
 *
 * <h3>Flux teste (T2 bonus)</h3>
 * <p>Une chambre en {@code MAINTENANCE} ne peut pas etre check-in : le service
 * {@code ChambreService.changerStatut(MAINTENANCE -> OCCUPEE)} leve une
 * {@link com.cityprojects.citybackend.exception.BusinessException} (transition
 * interdite). Le test attend une reponse 4xx.</p>
 *
 * <h3>Choix de dates</h3>
 * <p>Le DTO impose {@code @FutureOrPresent} sur {@code dateArrivee} et
 * {@code @Future} sur {@code dateDepart}. Le service refuse le check-in tant
 * que {@code dateArrivee.isAfter(today)}. La seule combinaison qui passe les
 * trois guards et permet un check-in puis check-out le meme jour de test est
 * {@code dateArrivee = today, dateDepart = today + 1} (1 nuit). On vit donc
 * avec 1 seule nuitee dans le scenario E2E - suffisant pour valider le
 * cycle complet.</p>
 *
 * <h3>Pas de @Transactional sur la classe</h3>
 * <p>Comme {@code ClientControllerIT} et {@code ReservationControllerIT} :
 * un {@code @Transactional} sur la classe rollback les INSERT et casserait le
 * load via repository depuis MockMvc (les requetes HTTP ouvrent leur propre
 * transaction). Cleanup explicite en {@link #setUp()} / {@link #tearDown()}.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class ReservationFlowE2EIT {

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
    private TypeChambreRepository typeChambreRepository;

    @Autowired
    private ChambreRepository chambreRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private MockMvc mockMvc;
    private TransactionTemplate transactionTemplate;

    private DBUser userGerant;
    private Long hotelMrId;
    private Long chambreId;
    private Long chambreMaintenanceId;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        transactionTemplate = new TransactionTemplate(transactionManager);
        cleanAll();

        // 1 hotel MR
        Hotel mr = new Hotel("MR1", "Hotel Mauritanie");
        mr.setCodePays("MR");
        hotelMrId = hotelRepository.saveAndFlush(mr).getHotelId();

        // Roles : on insere les roles utilises (cf. ClientControllerIT/ReservationControllerIT
        // pour la convention - on injecte ce qui sert).
        Role gerant = roleRepository.saveAndFlush(new Role("GERANT", "Gerant"));

        // Un seul user GERANT (cree clients + reservations + check-in/out).
        userGerant = userRepository.saveAndFlush(buildUser(
                "gerant1", "gerant1@mr.test", "Karim", "Sow", mr, gerant));

        // Catalogue minimal cote MR : 1 type, 2 chambres (1 dispo, 1 maintenance pour T2).
        try {
            TenantContext.set(hotelMrId);
            TypeChambre type = transactionTemplate.execute(s -> {
                TypeChambre t = new TypeChambre();
                t.setTypeCode("STD");
                t.setTypeNom("Standard");
                t.setNbLitsMax(2);
                t.setNbPersonnesMax(2);
                t.setActif(Boolean.TRUE);
                return typeChambreRepository.save(t);
            });
            Chambre dispo = transactionTemplate.execute(s -> {
                Chambre c = new Chambre();
                c.setNumeroChambre("101");
                c.setTypeId(type.getTypeId());
                c.setStatut(StatutChambre.DISPONIBLE);
                c.setNbLits(1);
                c.setNbPersonnesMax(2);
                c.setActif(Boolean.TRUE);
                return chambreRepository.save(c);
            });
            chambreId = dispo.getChambreId();
            Chambre maint = transactionTemplate.execute(s -> {
                Chambre c = new Chambre();
                c.setNumeroChambre("102");
                c.setTypeId(type.getTypeId());
                c.setStatut(StatutChambre.MAINTENANCE);
                c.setNbLits(1);
                c.setNbPersonnesMax(2);
                c.setActif(Boolean.TRUE);
                return chambreRepository.save(c);
            });
            chambreMaintenanceId = maint.getChambreId();
        } finally {
            TenantContext.clear();
        }

        // MockMvc avec la chaine de filtres Spring Security branchee
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
        jdbcTemplate.update("DELETE FROM hebergement.nuitees");
        jdbcTemplate.update("DELETE FROM hebergement.reservations_clients");
        jdbcTemplate.update("DELETE FROM hebergement.reservations_chambres");
        jdbcTemplate.update("DELETE FROM hebergement.reservations");
        jdbcTemplate.update("DELETE FROM hebergement.chambres");
        jdbcTemplate.update("DELETE FROM hebergement.types_chambres");
        jdbcTemplate.update("DELETE FROM client.clients");
        jdbcTemplate.update("DELETE FROM client.societes");
        jdbcTemplate.update("DELETE FROM finance.numerotation_sequence");
        jdbcTemplate.update("DELETE FROM core.dbusers");
        jdbcTemplate.update("DELETE FROM core.hotels");
        jdbcTemplate.update("DELETE FROM core.roles");
    }

    /**
     * Genere un JWT valide pour un user (chaine identique a celle de l'auth login).
     */
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
     * on caste via {@link Number#longValue()} pour rester compatible avec les
     * IDs futurs en {@code BIGINT}.</p>
     */
    private static Long readJsonLong(String body, String path) {
        Object value = JsonPath.read(body, path);
        return (value instanceof Number n) ? n.longValue() : null;
    }

    @Test
    @DisplayName("T1 - Cycle complet : creation client -> reservation -> check-in -> nuitees -> check-out -> CONSOMMEE")
    void shouldCompleteFullReservationLifecycle() throws Exception {
        String jwt = jwtFor(userGerant);

        // ---------- 1) CREATION CLIENT ----------
        String clientBody = "{"
                + "\"prenom\":\"Mariam\","
                + "\"nom\":\"Sow\","
                + "\"email\":\"msow@example.mr\","
                + "\"telephone\":\"+22245100100\""
                + "}";

        String clientResponse = mockMvc.perform(post("/api/clients")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(clientBody.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.clientId").exists())
                .andExpect(jsonPath("$.numeroClient", matchesPattern("CLI-\\d{4}-MR-\\d{6}")))
                .andExpect(jsonPath("$.nom").value("Sow"))
                .andExpect(jsonPath("$.prenom").value("Mariam"))
                .andExpect(jsonPath("$.hotelId").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        Long clientId = readJsonLong(clientResponse, "$.clientId");

        // ---------- 2) CREATION RESERVATION ----------
        // Choix dates : dateArrivee=today (FutureOrPresent OK + checkIn !isAfter(today) OK)
        //              dateDepart=today+1 (Future OK, 1 nuit)
        LocalDate dateArrivee = LocalDate.now();
        LocalDate dateDepart = dateArrivee.plusDays(1);

        String reservationBody = "{"
                + "\"clientPrincipalId\":" + clientId + ","
                + "\"dateArrivee\":\"" + dateArrivee + "\","
                + "\"dateDepart\":\"" + dateDepart + "\","
                + "\"nbAdultes\":2,"
                + "\"nbEnfants\":0,"
                + "\"reductionPourcentage\":0,"
                + "\"chambres\":[{"
                + "  \"chambreId\":" + chambreId + ","
                + "  \"prixNuit\":100.00"
                + "}]"
                + "}";

        String reservationResponse = mockMvc.perform(post("/api/hebergement/reservations")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(reservationBody.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reservationId").exists())
                .andExpect(jsonPath("$.numeroReservation",
                        matchesPattern("RES-\\d{4}-MR-\\d{6}")))
                .andExpect(jsonPath("$.statut").value("CONFIRMEE"))
                .andExpect(jsonPath("$.nbNuits").value(1))
                .andExpect(jsonPath("$.hotelId").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        Long reservationId = readJsonLong(reservationResponse, "$.reservationId");

        // ---------- 3) CHECK-IN (CONFIRMEE -> ARRIVEE) ----------
        mockMvc.perform(post("/api/hebergement/reservations/" + reservationId + "/check-in")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("ARRIVEE"));

        // ---------- 4) VERIFIER LES NUITEES ----------
        // 1 nuit prevue ([today, today+1)) -> CONSOMMEE puisque dateNuit=today
        // (checkIn marque CONSOMMEE pour !isAfter(today)).
        mockMvc.perform(get("/api/hebergement/reservations/" + reservationId + "/nuitees")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].dateNuitee").value(dateArrivee.toString()))
                .andExpect(jsonPath("$[0].statut").value("CONSOMMEE"));

        // ---------- 5) CHECK-OUT (ARRIVEE -> PARTIE) ----------
        // Aucune contrainte de date sur checkOut (cf. ReservationServiceImpl.checkOut),
        // on peut le faire le meme jour que le check-in.
        mockMvc.perform(post("/api/hebergement/reservations/" + reservationId + "/check-out")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("PARTIE"));

        // ---------- 6) NUITEES TOUTES CONSOMMEES ----------
        mockMvc.perform(get("/api/hebergement/reservations/" + reservationId + "/nuitees")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].statut").value("CONSOMMEE"));
    }

    @Test
    @DisplayName("T2 - Check-in refuse quand chambre en MAINTENANCE (transition interdite)")
    void shouldRejectCheckInWhenChambreInMaintenance() throws Exception {
        String jwt = jwtFor(userGerant);

        // 1) Creer un client
        String clientBody = "{"
                + "\"prenom\":\"Aicha\","
                + "\"nom\":\"Diop\","
                + "\"email\":\"adiop@example.mr\""
                + "}";
        String clientResponse = mockMvc.perform(post("/api/clients")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(clientBody.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long clientId = readJsonLong(clientResponse, "$.clientId");

        // 2) Creer une reservation sur la chambre MAINTENANCE.
        // Note : la creation est autorisee (le service ne verifie que l'attribut "actif",
        // pas le statut MAINTENANCE - cf. ReservationServiceImpl.create lignes 152-171).
        // Le rejet aura lieu au check-in via ChambreService.changerStatut.
        LocalDate dateArrivee = LocalDate.now();
        LocalDate dateDepart = dateArrivee.plusDays(1);
        String reservationBody = "{"
                + "\"clientPrincipalId\":" + clientId + ","
                + "\"dateArrivee\":\"" + dateArrivee + "\","
                + "\"dateDepart\":\"" + dateDepart + "\","
                + "\"nbAdultes\":1,"
                + "\"chambres\":[{"
                + "  \"chambreId\":" + chambreMaintenanceId + ","
                + "  \"prixNuit\":80.00"
                + "}]"
                + "}";
        String reservationResponse = mockMvc.perform(post("/api/hebergement/reservations")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(reservationBody.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long reservationId = readJsonLong(reservationResponse, "$.reservationId");

        // 3) Tenter le check-in : la chambre est en MAINTENANCE,
        // la transition MAINTENANCE -> OCCUPEE est interdite -> 4xx.
        mockMvc.perform(post("/api/hebergement/reservations/" + reservationId + "/check-in")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().is4xxClientError());
    }
}

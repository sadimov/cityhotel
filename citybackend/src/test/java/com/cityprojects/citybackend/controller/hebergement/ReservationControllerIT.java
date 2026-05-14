package com.cityprojects.citybackend.controller.hebergement;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.entity.client.Client;
import com.cityprojects.citybackend.entity.core.DBUser;
import com.cityprojects.citybackend.entity.core.Hotel;
import com.cityprojects.citybackend.entity.core.Role;
import com.cityprojects.citybackend.entity.hebergement.Chambre;
import com.cityprojects.citybackend.entity.hebergement.Reservation;
import com.cityprojects.citybackend.entity.hebergement.StatutChambre;
import com.cityprojects.citybackend.entity.hebergement.StatutReservation;
import com.cityprojects.citybackend.entity.hebergement.TypeChambre;
import com.cityprojects.citybackend.repository.client.ClientRepository;
import com.cityprojects.citybackend.repository.core.DBUserRepository;
import com.cityprojects.citybackend.repository.core.HotelRepository;
import com.cityprojects.citybackend.repository.core.RoleRepository;
import com.cityprojects.citybackend.repository.hebergement.ChambreRepository;
import com.cityprojects.citybackend.repository.hebergement.ReservationRepository;
import com.cityprojects.citybackend.repository.hebergement.TypeChambreRepository;
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

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test d'integration Failsafe : valide la chaine complete
 * JWT &rarr; {@code @PreAuthorize} &rarr; {@code @TenantId} &rarr; DB sur le
 * controller reservation (Tour 11).
 *
 * <h3>Stack</h3>
 * <p>MockMvc + JWT factice signe via {@link JwtTokenProvider} (meme cle que la
 * prod, profil "test"). H2 in-memory en mode PostgreSQL via le profil "test"
 * (Liquibase desactive : on insere les hotels, roles, users, chambres et
 * clients en DDL/JPA directement). Pas de Testcontainers ici.</p>
 *
 * <h3>Cas couverts</h3>
 * <ol>
 *   <li>T1 : POST /api/hebergement/reservations avec JWT GERANT du hotel MR -&gt; 201,
 *       numero RES-{annee}-MR-{seq}, pas de hotelId expose.</li>
 *   <li>T2 : MAGASIN tente GET /api/hebergement/reservations -&gt; 403 (matrice
 *       {@code @PreAuthorize} n'inclut pas MAGASIN).</li>
 *   <li>T3 : JWT hotel FR lit reservation hotel MR -&gt; 404 (Hibernate filtre).</li>
 * </ol>
 *
 * <p>Tour 14 audit B1 : prefixe migre {@code /api/hebergement/reservations} -&gt;
 * {@code /api/hebergement/reservations}.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class ReservationControllerIT {

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
    private TypeChambreRepository typeChambreRepository;

    @Autowired
    private ChambreRepository chambreRepository;

    @Autowired
    private ReservationRepository reservationRepository;

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
    private Long clientMrId;
    private Long chambreMrId;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        transactionTemplate = new TransactionTemplate(transactionManager);

        // Cleanup ordonne
        // Tour 44 Phase 1 : la chaine create() reservation genere desormais
        // facture previsionnelle + lignes + DEBIT compte client. Purger finance
        // d'abord (FK lignes_factures.nuitee_id et factures.reservation_id).
        jdbcTemplate.update("DELETE FROM finance.affectations_paiements");
        jdbcTemplate.update("DELETE FROM finance.operations_comptes");
        jdbcTemplate.update("DELETE FROM finance.paiements");
        jdbcTemplate.update("DELETE FROM finance.lignes_factures");
        jdbcTemplate.update("DELETE FROM finance.factures");
        jdbcTemplate.update("DELETE FROM finance.comptes");
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

        // Hotels MR + FR
        Hotel mr = new Hotel("MR1", "Hotel Mauritanie");
        mr.setCodePays("MR");
        hotelMrId = hotelRepository.saveAndFlush(mr).getHotelId();

        Hotel fr = new Hotel("FR1", "Hotel France");
        fr.setCodePays("FR");
        hotelFrId = hotelRepository.saveAndFlush(fr).getHotelId();

        // Roles
        Role gerant = roleRepository.saveAndFlush(new Role("GERANT", "Gerant"));
        Role magasin = roleRepository.saveAndFlush(new Role("MAGASIN", "Magasin"));

        // Users : 1 gerant + 1 magasin sur MR, 1 gerant sur FR
        userGerantHotelMr = userRepository.saveAndFlush(buildUser(
                "gerant1", "gerant1@mr.test", "Karim", "Sow", mr, gerant));
        userMagasinHotelMr = userRepository.saveAndFlush(buildUser(
                "magasin1", "magasin1@mr.test", "Sidi", "Cheikh", mr, magasin));
        userGerantHotelFr = userRepository.saveAndFlush(buildUser(
                "gerant2", "gerant2@fr.test", "Pierre", "Dupont", fr, gerant));

        // Catalogue minimal cote MR : 1 type, 1 chambre, 1 client
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
            Chambre chambre = transactionTemplate.execute(s -> {
                Chambre c = new Chambre();
                c.setNumeroChambre("201");
                c.setTypeId(type.getTypeId());
                c.setStatut(StatutChambre.DISPONIBLE);
                c.setNbLits(1);
                c.setNbPersonnesMax(2);
                c.setActif(Boolean.TRUE);
                return chambreRepository.save(c);
            });
            chambreMrId = chambre.getChambreId();
            Client client = transactionTemplate.execute(s -> {
                Client cl = new Client();
                cl.setNumeroClient("CLI-2026-MR-000001");
                cl.setPrenom("Mariam");
                cl.setNom("Diallo");
                cl.setActif(Boolean.TRUE);
                return clientRepository.save(cl);
            });
            clientMrId = client.getClientId();
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
        // Tour 44 Phase 1 : la chaine create() reservation genere desormais
        // facture previsionnelle + lignes + DEBIT compte client. Purger finance
        // d'abord (FK lignes_factures.nuitee_id et factures.reservation_id).
        jdbcTemplate.update("DELETE FROM finance.affectations_paiements");
        jdbcTemplate.update("DELETE FROM finance.operations_comptes");
        jdbcTemplate.update("DELETE FROM finance.paiements");
        jdbcTemplate.update("DELETE FROM finance.lignes_factures");
        jdbcTemplate.update("DELETE FROM finance.factures");
        jdbcTemplate.update("DELETE FROM finance.comptes");
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

    private String jwtFor(DBUser user) {
        UserPrincipal principal = UserPrincipal.create(user, Collections.singletonList(
                new SimpleGrantedAuthority("ROLE_" + user.getRole().getRoleCode())));
        return jwtTokenProvider.generateTokenForUser(principal);
    }

    /**
     * Cree une reservation directement via JPA (hors HTTP) dans le tenant
     * indique pour preparer un cas cross-tenant. Utilise {@link TenantContext}
     * le temps de la transaction d'insert.
     */
    private Long createReservationInTenant(Long hotelId, Long clientId, Long userId) {
        try {
            TenantContext.set(hotelId);
            return transactionTemplate.execute(s -> {
                Reservation r = new Reservation();
                r.setNumeroReservation("RES-2026-XX-999999");
                r.setClientPrincipalId(clientId);
                r.setDateArrivee(LocalDate.now().plusDays(3));
                r.setDateDepart(LocalDate.now().plusDays(5));
                r.setNbNuits(2);
                r.setNbAdultes(1);
                r.setNbEnfants(0);
                r.setStatut(StatutReservation.CONFIRMEE);
                r.setReductionPourcentage(BigDecimal.ZERO);
                r.setMontantTotal(new BigDecimal("100.00"));
                r.setUserId(userId);
                return reservationRepository.save(r).getReservationId();
            });
        } finally {
            TenantContext.clear();
        }
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

    @Test
    @DisplayName("T1 - GERANT hotel MR cree une reservation : 201, format RES-{annee}-MR-{seq}, pas de hotelId expose")
    void shouldCreateReservationWhenGerant() throws Exception {
        String jwt = jwtFor(userGerantHotelMr);
        LocalDate arrivee = LocalDate.now().plusDays(2);
        LocalDate depart = arrivee.plusDays(2);

        String body = "{"
                + "\"clientPrincipalId\":" + clientMrId + ","
                + "\"dateArrivee\":\"" + arrivee + "\","
                + "\"dateDepart\":\"" + depart + "\","
                + "\"nbAdultes\":2,"
                + "\"nbEnfants\":0,"
                + "\"reductionPourcentage\":0,"
                + "\"chambres\":[{"
                + "  \"chambreId\":" + chambreMrId + ","
                + "  \"prixNuit\":120.00"
                + "}]"
                + "}";

        mockMvc.perform(post("/api/hebergement/reservations")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(body.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reservationId").exists())
                .andExpect(jsonPath("$.numeroReservation",
                        matchesPattern("RES-\\d{4}-MR-\\d{6}")))
                .andExpect(jsonPath("$.statut").value("CONFIRMEE"))
                .andExpect(jsonPath("$.nbNuits").value(2))
                // Le DTO de sortie ne doit JAMAIS exposer hotelId.
                .andExpect(jsonPath("$.hotelId").doesNotExist());
    }

    @Test
    @DisplayName("T2 - MAGASIN tente GET /api/hebergement/reservations : 403 (matrice @PreAuthorize n'inclut pas MAGASIN)")
    void shouldDenyAccessForMagasin() throws Exception {
        String jwt = jwtFor(userMagasinHotelMr);

        mockMvc.perform(get("/api/hebergement/reservations")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("T3 - JWT hotel FR lit reservation hotel MR : 404 (Hibernate filtre via @TenantId)")
    void shouldReturn404ForCrossTenantRead() throws Exception {
        // Cree une reservation cote MR via JPA direct
        Long mrReservationId = createReservationInTenant(
                hotelMrId, clientMrId, userGerantHotelMr.getUserId());

        // Tente de la lire avec un JWT du hotel FR
        String jwt = jwtFor(userGerantHotelFr);

        mockMvc.perform(get("/api/hebergement/reservations/" + mrReservationId)
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isNotFound());
    }
}

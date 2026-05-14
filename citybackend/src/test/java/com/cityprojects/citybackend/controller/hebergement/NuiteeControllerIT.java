package com.cityprojects.citybackend.controller.hebergement;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.dto.hebergement.ReservationChambreCreateDto;
import com.cityprojects.citybackend.dto.hebergement.ReservationCreateDto;
import com.cityprojects.citybackend.dto.hebergement.ReservationDto;
import com.cityprojects.citybackend.entity.client.Client;
import com.cityprojects.citybackend.entity.core.DBUser;
import com.cityprojects.citybackend.entity.core.Hotel;
import com.cityprojects.citybackend.entity.core.Role;
import com.cityprojects.citybackend.entity.hebergement.Chambre;
import com.cityprojects.citybackend.entity.hebergement.StatutChambre;
import com.cityprojects.citybackend.entity.hebergement.TypeChambre;
import com.cityprojects.citybackend.repository.client.ClientRepository;
import com.cityprojects.citybackend.repository.core.DBUserRepository;
import com.cityprojects.citybackend.repository.core.HotelRepository;
import com.cityprojects.citybackend.repository.core.RoleRepository;
import com.cityprojects.citybackend.repository.hebergement.ChambreRepository;
import com.cityprojects.citybackend.repository.hebergement.TypeChambreRepository;
import com.cityprojects.citybackend.security.JwtTokenProvider;
import com.cityprojects.citybackend.security.UserPrincipal;
import com.cityprojects.citybackend.service.hebergement.ReservationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test d'integration Failsafe du {@link NuiteeController} (Tour 14 B2 API).
 *
 * <h3>Couverture</h3>
 * <ol>
 *   <li>T1 : GET /api/hebergement/nuitees/reservation/{id} avec JWT GERANT MR
 *       -&gt; 200 + liste des nuitees, sans hotelId expose.</li>
 *   <li>T2 : MAGASIN tente le meme GET -&gt; 403 (matrice {@code @PreAuthorize}).</li>
 * </ol>
 */
@SpringBootTest
@ActiveProfiles("test")
class NuiteeControllerIT {

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
    private ReservationService reservationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private MockMvc mockMvc;
    private TransactionTemplate transactionTemplate;

    private DBUser userGerantHotelMr;
    private DBUser userMagasinHotelMr;
    private Long hotelMrId;
    private Long clientMrId;
    private Long chambreMrId;
    private Long userMrId;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
        transactionTemplate = new TransactionTemplate(transactionManager);

        cleanAll();

        Hotel mr = new Hotel("MR1", "Hotel Mauritanie");
        mr.setCodePays("MR");
        hotelMrId = hotelRepository.saveAndFlush(mr).getHotelId();

        Role gerant = roleRepository.saveAndFlush(new Role("GERANT", "Gerant"));
        Role magasin = roleRepository.saveAndFlush(new Role("MAGASIN", "Magasin"));

        userGerantHotelMr = userRepository.saveAndFlush(buildUser(
                "gerant1", "gerant1@mr.test", "Karim", "Sow", mr, gerant));
        userMagasinHotelMr = userRepository.saveAndFlush(buildUser(
                "magasin1", "magasin1@mr.test", "Sidi", "Cheikh", mr, magasin));
        userMrId = userGerantHotelMr.getUserId();

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
                c.setNumeroChambre("301");
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
                cl.setNumeroClient("CLI-2026-MR-000077");
                cl.setPrenom("Aicha");
                cl.setNom("Bint");
                cl.setActif(Boolean.TRUE);
                return clientRepository.save(cl);
            });
            clientMrId = client.getClientId();
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
        SecurityContextHolder.clearContext();
        cleanAll();
    }

    private void cleanAll() {
        // Tour 44 Phase 1 : create() reservation genere facture previsionnelle.
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
     * Cree une reservation via {@link ReservationService} en positionnant
     * tenant + securityContext (le service lit userId du SecurityContext).
     */
    private Long createReservationInTenant() {
        try {
            TenantContext.set(hotelMrId);
            UserPrincipal principal = new UserPrincipal(
                    userMrId, "gerant1", "gerant1@mr.test", "pwd",
                    "Karim", "Sow", hotelMrId, "MR1", "Hotel Mauritanie",
                    "GERANT", "GERANT", Boolean.TRUE, Boolean.FALSE,
                    Collections.singletonList(new SimpleGrantedAuthority("ROLE_GERANT")));
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

            LocalDate arrivee = LocalDate.now().plusDays(1);
            LocalDate depart = arrivee.plusDays(2);
            ReservationCreateDto dto = new ReservationCreateDto(
                    clientMrId, null, arrivee, depart, 1, 0, null, null, BigDecimal.ZERO,
                    List.of(new ReservationChambreCreateDto(chambreMrId, null, null, new BigDecimal("100.00"))),
                    null);
            ReservationDto created = transactionTemplate.execute(s -> reservationService.create(dto));
            return created.reservationId();
        } finally {
            TenantContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("T1 - GERANT MR liste les nuitees d'une reservation : 200, pas de hotelId expose")
    void shouldListNuiteesByReservation() throws Exception {
        Long reservationId = createReservationInTenant();
        String jwt = jwtFor(userGerantHotelMr);

        mockMvc.perform(get("/api/hebergement/nuitees/reservation/" + reservationId)
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").exists())
                .andExpect(jsonPath("$[0].dateNuitee").exists())
                .andExpect(jsonPath("$[0].reservationId").value(reservationId.intValue()))
                .andExpect(jsonPath("$[0].chambreId").value(chambreMrId.intValue()))
                // hotelId NE doit JAMAIS apparaitre.
                .andExpect(jsonPath("$[0].hotelId").doesNotExist());
    }

    @Test
    @DisplayName("T2 - MAGASIN tente GET /api/hebergement/nuitees/reservation/{id} : 403")
    void shouldDenyMagasinAccess() throws Exception {
        Long reservationId = createReservationInTenant();
        String jwt = jwtFor(userMagasinHotelMr);

        mockMvc.perform(get("/api/hebergement/nuitees/reservation/" + reservationId)
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isForbidden());
    }
}

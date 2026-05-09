package com.cityprojects.citybackend.controller.menage;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.entity.core.DBUser;
import com.cityprojects.citybackend.entity.core.Hotel;
import com.cityprojects.citybackend.entity.core.Role;
import com.cityprojects.citybackend.entity.hebergement.Chambre;
import com.cityprojects.citybackend.entity.hebergement.StatutChambre;
import com.cityprojects.citybackend.entity.hebergement.TypeChambre;
import com.cityprojects.citybackend.entity.menage.Personnel;
import com.cityprojects.citybackend.entity.menage.StatutTache;
import com.cityprojects.citybackend.entity.menage.Tache;
import com.cityprojects.citybackend.entity.menage.TypeNettoyage;
import com.cityprojects.citybackend.repository.core.DBUserRepository;
import com.cityprojects.citybackend.repository.core.HotelRepository;
import com.cityprojects.citybackend.repository.core.RoleRepository;
import com.cityprojects.citybackend.repository.hebergement.ChambreRepository;
import com.cityprojects.citybackend.repository.hebergement.TypeChambreRepository;
import com.cityprojects.citybackend.repository.menage.PersonnelRepository;
import com.cityprojects.citybackend.repository.menage.TacheRepository;
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
import java.time.LocalDate;
import java.util.Collections;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test d'integration Failsafe (Tour 27) du {@link TacheController}.
 *
 * <h3>Cas couverts</h3>
 * <ol>
 *   <li>T1 : MENAGE cree une tache -&gt; 201, statut PLANIFIEE,
 *       pas de hotelId expose.</li>
 *   <li>T2 : JWT hotel FR lit tache de hotel MR -&gt; 404 (Hibernate filtre via @TenantId).</li>
 * </ol>
 */
@SpringBootTest
@ActiveProfiles("test")
class TacheControllerIT {

    @Autowired private WebApplicationContext webApplicationContext;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private DBUserRepository userRepository;
    @Autowired private HotelRepository hotelRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private TypeChambreRepository typeChambreRepository;
    @Autowired private ChambreRepository chambreRepository;
    @Autowired private PersonnelRepository personnelRepository;
    @Autowired private TacheRepository tacheRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;

    private MockMvc mockMvc;
    private TransactionTemplate tx;

    private DBUser userMenageMr;
    private DBUser userGerantFr;
    private Long chambreMrId;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        tx = new TransactionTemplate(transactionManager);
        cleanAll();

        Hotel mr = new Hotel("MR1", "Hotel Mauritanie");
        mr.setCodePays("MR");
        Long hotelMrId = hotelRepository.saveAndFlush(mr).getHotelId();

        Hotel fr = new Hotel("FR1", "Hotel France");
        fr.setCodePays("FR");
        hotelRepository.saveAndFlush(fr);

        Role menage = roleRepository.saveAndFlush(new Role("MENAGE", "Menage"));
        Role gerant = roleRepository.saveAndFlush(new Role("GERANT", "Gerant"));

        userMenageMr = userRepository.saveAndFlush(buildUser(
                "menage1", "menage1@mr.test", "Aly", "Menage", mr, menage));
        userGerantFr = userRepository.saveAndFlush(buildUser(
                "gerant_fr", "gerant_fr@fr.test", "Pierre", "Gerant", fr, gerant));

        // Seed chambre + personnel dans MR
        try {
            TenantContext.set(hotelMrId);
            TypeChambre type = tx.execute(s -> {
                TypeChambre t = new TypeChambre();
                t.setTypeCode("STD");
                t.setTypeNom("Standard");
                t.setNbLitsMax(2);
                t.setNbPersonnesMax(2);
                t.setActif(Boolean.TRUE);
                return typeChambreRepository.save(t);
            });
            Chambre chambre = tx.execute(s -> {
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
        jdbcTemplate.update("DELETE FROM menage.historique");
        jdbcTemplate.update("DELETE FROM menage.taches");
        jdbcTemplate.update("DELETE FROM menage.planning");
        jdbcTemplate.update("DELETE FROM menage.personnel");
        jdbcTemplate.update("DELETE FROM hebergement.reservations_chambres");
        jdbcTemplate.update("DELETE FROM hebergement.reservations_clients");
        jdbcTemplate.update("DELETE FROM hebergement.nuitees");
        jdbcTemplate.update("DELETE FROM hebergement.reservations");
        jdbcTemplate.update("DELETE FROM hebergement.chambres");
        jdbcTemplate.update("DELETE FROM hebergement.tarifs_chambres");
        jdbcTemplate.update("DELETE FROM hebergement.types_chambres");
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

    /** Cree une tache directement (JPA) dans le tenant donne, sans passer par l'API. */
    private Long createTacheDirectInTenant(Long hotelId, Long chambreId) {
        try {
            TenantContext.set(hotelId);
            return tx.execute(s -> {
                // Cree d'abord un personnel (pas obligatoire mais minimal)
                Personnel p = new Personnel();
                p.setNumeroEmploye("MEN-IT-X");
                p.setPrenom("Direct");
                p.setNom("Seed");
                p.setActif(Boolean.TRUE);
                personnelRepository.save(p);

                Tache t = new Tache();
                t.setChambreId(chambreId);
                t.setStatut(StatutTache.PLANIFIEE);
                t.setTypeNettoyage(TypeNettoyage.QUOTIDIEN);
                t.setPriorite(1);
                t.setDatePlanifiee(LocalDate.now());
                return tacheRepository.save(t).getTacheId();
            });
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("T1 - MENAGE cree une tache : 201, statut PLANIFIEE, pas de hotelId expose")
    void shouldCreateTacheWhenMenage() throws Exception {
        String jwt = jwtFor(userMenageMr);
        String body = "{"
                + "\"chambreId\":" + chambreMrId + ","
                + "\"typeNettoyage\":\"QUOTIDIEN\","
                + "\"priorite\":2,"
                + "\"datePlanifiee\":\"" + LocalDate.now() + "\","
                + "\"commentaires\":\"Nettoyage post-checkout\""
                + "}";

        mockMvc.perform(post("/api/menage/taches")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(body.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tacheId").exists())
                .andExpect(jsonPath("$.chambreId").value(chambreMrId))
                .andExpect(jsonPath("$.statut").value("PLANIFIEE"))
                .andExpect(jsonPath("$.typeNettoyage").value("QUOTIDIEN"))
                .andExpect(jsonPath("$.priorite").value(2))
                // Pas de hotelId expose
                .andExpect(jsonPath("$.hotelId").doesNotExist());
    }

    @Test
    @DisplayName("T2 - JWT hotel FR lit tache de hotel MR : 404 (Hibernate filtre via @TenantId)")
    void shouldReturn404ForCrossTenantRead() throws Exception {
        Long hotelMrId = hotelRepository.findAll().stream()
                .filter(h -> h.getHotelCode().equals("MR1"))
                .findFirst().orElseThrow().getHotelId();

        Long mrTacheId = createTacheDirectInTenant(hotelMrId, chambreMrId);

        String jwt = jwtFor(userGerantFr);

        mockMvc.perform(get("/api/menage/taches/" + mrTacheId)
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isNotFound());
    }
}

package com.cityprojects.citybackend.service.hebergement;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.dto.hebergement.NuiteeDto;
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
import com.cityprojects.citybackend.exception.ResourceNotFoundException;
import com.cityprojects.citybackend.repository.client.ClientRepository;
import com.cityprojects.citybackend.repository.core.DBUserRepository;
import com.cityprojects.citybackend.repository.core.HotelRepository;
import com.cityprojects.citybackend.repository.core.RoleRepository;
import com.cityprojects.citybackend.repository.hebergement.ChambreRepository;
import com.cityprojects.citybackend.repository.hebergement.TypeChambreRepository;
import com.cityprojects.citybackend.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests Surefire (rapides, en H2) du {@link NuiteeService} (Tour 14 B2 API).
 *
 * <h3>Couverture</h3>
 * <ol>
 *   <li>T1 : findByReservation() retourne les nuitees ordonnees par date
 *       croissante.</li>
 *   <li>T2 : findByChambre() avec page non triee applique le tri par defaut
 *       stable {@code dateNuit DESC, nuiteeId ASC}.</li>
 *   <li>T3 : findByReservation() depuis un autre tenant -&gt;
 *       {@link ResourceNotFoundException} (isolation @TenantId).</li>
 * </ol>
 *
 * <p>Strategie : on cree une reservation via {@link ReservationService} (qui
 * genere les nuitees automatiquement), puis on lit via {@link NuiteeService}.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class NuiteeServiceTests {

    @Autowired
    private NuiteeService nuiteeService;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private HotelRepository hotelRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private DBUserRepository userRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private TypeChambreRepository typeChambreRepository;

    @Autowired
    private ChambreRepository chambreRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;
    private Long hotelMrId;
    private Long hotelFrId;
    private Long userMrId;
    private Long clientMrId;
    private Long chambreMrId;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        TenantContext.clear();
        SecurityContextHolder.clearContext();
        cleanAll();

        Hotel mr = new Hotel("MRH001", "Hotel Mauritanie");
        mr.setCodePays("MR");
        hotelMrId = hotelRepository.saveAndFlush(mr).getHotelId();

        Hotel fr = new Hotel("FRH001", "Hotel France");
        fr.setCodePays("FR");
        hotelFrId = hotelRepository.saveAndFlush(fr).getHotelId();

        Role gerant = roleRepository.saveAndFlush(new Role("GERANT", "Gerant"));

        DBUser user = new DBUser("recept1", "recept1@h1.test",
                "$2a$12$placeholderhashplaceholderhashplaceholderhashplacehash",
                "Mariam", "Sow", mr, gerant);
        user.setActif(Boolean.TRUE);
        user.setCompteVerrouille(Boolean.FALSE);
        userMrId = userRepository.saveAndFlush(user).getUserId();

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
                c.setNumeroChambre("101");
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
                cl.setNumeroClient("CLI-2026-MR-000099");
                cl.setPrenom("Sidi");
                cl.setNom("Mohamed");
                cl.setActif(Boolean.TRUE);
                return clientRepository.save(cl);
            });
            clientMrId = client.getClientId();
        } finally {
            TenantContext.clear();
        }
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
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

    private void authenticateAs(Long userId, Long hotelId, String roleCode) {
        UserPrincipal principal = new UserPrincipal(
                userId, "test-user", "test@h.test", "pwd",
                "Test", "User", hotelId, "MRH001", "Hotel Test",
                roleCode, roleCode, Boolean.TRUE, Boolean.FALSE,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + roleCode)));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @Test
    @DisplayName("T1 - findByReservation() retourne les 3 nuitees ordonnees par date croissante (DTO sans hotelId)")
    void shouldReturnNuiteesByReservationOrderedAsc() {
        TenantContext.set(hotelMrId);
        authenticateAs(userMrId, hotelMrId, "GERANT");

        LocalDate arrivee = LocalDate.now().plusDays(1);
        LocalDate depart = arrivee.plusDays(3);
        ReservationCreateDto dto = new ReservationCreateDto(
                clientMrId, null, arrivee, depart, 1, 0, null, null, BigDecimal.ZERO,
                List.of(new ReservationChambreCreateDto(chambreMrId, null, null, new BigDecimal("100.00"))),
                null);
        ReservationDto created = transactionTemplate.execute(s -> reservationService.create(dto));

        List<NuiteeDto> nuitees = transactionTemplate.execute(s ->
                nuiteeService.findByReservation(created.reservationId()));

        assertNotNull(nuitees);
        assertEquals(3, nuitees.size(), "3 nuitees doivent etre creees pour 3 nuits");
        // Ordre : croissant
        assertTrue(nuitees.get(0).dateNuitee().isBefore(nuitees.get(1).dateNuitee()));
        assertTrue(nuitees.get(1).dateNuitee().isBefore(nuitees.get(2).dateNuitee()));
        // Pas de hotelId dans le DTO (record n'a pas le champ ; juste verif coverage)
        assertEquals(arrivee, nuitees.get(0).dateNuitee());
        assertEquals(chambreMrId, nuitees.get(0).chambreId());
        assertEquals(created.reservationId(), nuitees.get(0).reservationId());
        assertNotNull(nuitees.get(0).id(), "Le DTO expose 'id' (alias de nuiteeId)");
    }

    @Test
    @DisplayName("T2 - findByChambre() applique le tri par defaut (dateNuit DESC, nuiteeId ASC) sur Pageable non trie")
    void shouldApplyDefaultStableSortOnUnsortedPageable() {
        TenantContext.set(hotelMrId);
        authenticateAs(userMrId, hotelMrId, "GERANT");

        LocalDate arrivee = LocalDate.now().plusDays(1);
        LocalDate depart = arrivee.plusDays(3);
        ReservationCreateDto dto = new ReservationCreateDto(
                clientMrId, null, arrivee, depart, 1, 0, null, null, BigDecimal.ZERO,
                List.of(new ReservationChambreCreateDto(chambreMrId, null, null, new BigDecimal("80.00"))),
                null);
        transactionTemplate.execute(s -> reservationService.create(dto));

        // Pageable sans tri explicite : on attend dateNuit DESC.
        Page<NuiteeDto> page = transactionTemplate.execute(s ->
                nuiteeService.findByChambre(chambreMrId, PageRequest.of(0, 10)));

        assertNotNull(page);
        assertEquals(3L, page.getTotalElements());
        List<NuiteeDto> content = page.getContent();
        assertFalse(content.isEmpty());
        // dateNuit DESC : la plus recente en premier
        assertTrue(content.get(0).dateNuitee().isAfter(content.get(content.size() - 1).dateNuitee()),
                "tri stable par dateNuit DESC attendu");
    }

    @Test
    @DisplayName("T3 - findByReservation() depuis un autre tenant -> ResourceNotFoundException (@TenantId)")
    void shouldRejectCrossTenantRead() {
        // Cree une reservation cote hotel MR.
        TenantContext.set(hotelMrId);
        authenticateAs(userMrId, hotelMrId, "GERANT");
        LocalDate arrivee = LocalDate.now().plusDays(2);
        LocalDate depart = arrivee.plusDays(1);
        ReservationCreateDto dto = new ReservationCreateDto(
                clientMrId, null, arrivee, depart, 1, 0, null, null, BigDecimal.ZERO,
                List.of(new ReservationChambreCreateDto(chambreMrId, null, null, new BigDecimal("90.00"))),
                null);
        ReservationDto created = transactionTemplate.execute(s -> reservationService.create(dto));
        TenantContext.clear();
        SecurityContextHolder.clearContext();

        // Tente lecture depuis hotel FR
        TenantContext.set(hotelFrId);
        authenticateAs(99L, hotelFrId, "GERANT");
        Long foreignId = created.reservationId();
        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> transactionTemplate.execute(s -> nuiteeService.findByReservation(foreignId)));
        assertEquals("error.reservation.notFound", ex.getMessage());
    }
}

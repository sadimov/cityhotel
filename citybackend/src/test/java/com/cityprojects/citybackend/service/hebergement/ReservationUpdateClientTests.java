package com.cityprojects.citybackend.service.hebergement;

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
import com.cityprojects.citybackend.exception.BusinessException;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests Surefire (H2) du Tour 49 : changement du client principal dans
 * {@link ReservationService#update(Long, ReservationCreateDto)}.
 *
 * <h3>Couverture</h3>
 * <ol>
 *   <li>T1 : update() change clientPrincipalId vers un client valide -&gt; OK.</li>
 *   <li>T2 : update() vers un client INACTIF -&gt; BusinessException
 *       {@code error.client.inactif}.</li>
 *   <li>T3 : update() vers un client cross-tenant (hotel FR) -&gt;
 *       ResourceNotFoundException (Hibernate filtre via @TenantId, le
 *       findById renvoie empty).</li>
 * </ol>
 */
@SpringBootTest
@ActiveProfiles("test")
class ReservationUpdateClientTests {

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
    private Long clientMrAltId;
    private Long clientMrInactifId;
    private Long clientFrId;
    private Long chambreMrId;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        TenantContext.clear();
        SecurityContextHolder.clearContext();

        // Cleanup ordonne (FK)
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
        Hotel mr = new Hotel("MRH001", "Hotel Mauritanie");
        mr.setCodePays("MR");
        hotelMrId = hotelRepository.saveAndFlush(mr).getHotelId();

        Hotel fr = new Hotel("FRH001", "Hotel France");
        fr.setCodePays("FR");
        hotelFrId = hotelRepository.saveAndFlush(fr).getHotelId();

        // Role + User MR
        Role gerant = roleRepository.saveAndFlush(new Role("GERANT", "Gerant"));
        DBUser user = new DBUser("recept1", "recept1@h1.test",
                "$2a$12$placeholderhashplaceholderhashplaceholderhashplacehash",
                "Mariam", "Sow", mr, gerant);
        user.setActif(Boolean.TRUE);
        user.setCompteVerrouille(Boolean.FALSE);
        userMrId = userRepository.saveAndFlush(user).getUserId();

        // Catalogue MR : TypeChambre + Chambre + 3 clients (MR)
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

            Client cl = transactionTemplate.execute(s -> {
                Client c = new Client();
                c.setNumeroClient("CLI-2026-MR-000001");
                c.setPrenom("Sidi");
                c.setNom("Mohamed");
                c.setActif(Boolean.TRUE);
                return clientRepository.save(c);
            });
            clientMrId = cl.getClientId();

            Client cl2 = transactionTemplate.execute(s -> {
                Client c = new Client();
                c.setNumeroClient("CLI-2026-MR-000002");
                c.setPrenom("Fatim");
                c.setNom("Diallo");
                c.setActif(Boolean.TRUE);
                return clientRepository.save(c);
            });
            clientMrAltId = cl2.getClientId();

            Client cl3 = transactionTemplate.execute(s -> {
                Client c = new Client();
                c.setNumeroClient("CLI-2026-MR-000003");
                c.setPrenom("Inactif");
                c.setNom("Banni");
                c.setActif(Boolean.FALSE);
                return clientRepository.save(c);
            });
            clientMrInactifId = cl3.getClientId();
        } finally {
            TenantContext.clear();
        }

        // Client cote FR (cross-tenant)
        try {
            TenantContext.set(hotelFrId);
            Client clFr = transactionTemplate.execute(s -> {
                Client c = new Client();
                c.setNumeroClient("CLI-2026-FR-000001");
                c.setPrenom("Etranger");
                c.setNom("CrossTenant");
                c.setActif(Boolean.TRUE);
                return clientRepository.save(c);
            });
            clientFrId = clFr.getClientId();
        } finally {
            TenantContext.clear();
        }
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
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

    private void authenticateAs(Long userId, Long hotelId, String roleCode) {
        UserPrincipal principal = new UserPrincipal(
                userId, "test-user", "test@h.test", "pwd",
                "Test", "User", hotelId, "MRH001", "Hotel Test",
                roleCode, roleCode, Boolean.TRUE, Boolean.FALSE,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + roleCode)));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private ReservationDto createBaseReservation() {
        LocalDate arrivee = LocalDate.now().plusDays(2);
        LocalDate depart = arrivee.plusDays(2);
        ReservationCreateDto dto = new ReservationCreateDto(
                clientMrId, null, arrivee, depart, 1, 0,
                null, null, BigDecimal.ZERO,
                List.of(new ReservationChambreCreateDto(
                        chambreMrId, null, null, new BigDecimal("80.00"))),
                null);
        return transactionTemplate.execute(s -> reservationService.create(dto));
    }

    @Test
    @DisplayName("T1 - update() change clientPrincipalId vers un client actif -> OK")
    void shouldChangeClientPrincipalToValidClient() {
        TenantContext.set(hotelMrId);
        authenticateAs(userMrId, hotelMrId, "GERANT");
        ReservationDto created = createBaseReservation();
        assertNotNull(created.reservationId());
        assertEquals(clientMrId, created.clientPrincipalId());

        // Update : passer du clientMr au clientMrAlt
        ReservationCreateDto updateDto = new ReservationCreateDto(
                clientMrAltId, null,
                created.dateArrivee(), created.dateDepart(),
                created.nbAdultes(), created.nbEnfants(),
                created.motifSejour(), created.commentaires(),
                created.reductionPourcentage(),
                // chambres : on n'autorise pas la modification ici, mais le DTO
                // exige @NotEmpty - on reutilise la meme chambre.
                List.of(new ReservationChambreCreateDto(
                        chambreMrId, null, null, new BigDecimal("80.00"))),
                null);

        ReservationDto updated = transactionTemplate.execute(s ->
                reservationService.update(created.reservationId(), updateDto));
        assertEquals(clientMrAltId, updated.clientPrincipalId(),
                "Le clientPrincipalId doit avoir change vers clientMrAlt");
    }

    @Test
    @DisplayName("T2 - update() vers un client INACTIF -> BusinessException error.client.inactif")
    void shouldRejectChangeToInactiveClient() {
        TenantContext.set(hotelMrId);
        authenticateAs(userMrId, hotelMrId, "GERANT");
        ReservationDto created = createBaseReservation();
        Long resId = created.reservationId();

        ReservationCreateDto updateDto = new ReservationCreateDto(
                clientMrInactifId, null,
                created.dateArrivee(), created.dateDepart(),
                1, 0, null, null, BigDecimal.ZERO,
                List.of(new ReservationChambreCreateDto(
                        chambreMrId, null, null, new BigDecimal("80.00"))),
                null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> transactionTemplate.execute(s ->
                        reservationService.update(resId, updateDto)));
        assertEquals("error.client.inactif", ex.getMessage());
    }

    @Test
    @DisplayName("T3 - update() vers un client cross-tenant -> ResourceNotFoundException")
    void shouldRejectChangeToCrossTenantClient() {
        TenantContext.set(hotelMrId);
        authenticateAs(userMrId, hotelMrId, "GERANT");
        ReservationDto created = createBaseReservation();
        Long resId = created.reservationId();

        // clientFrId existe en BDD mais n'est PAS visible depuis hotelMr
        // (Hibernate @TenantId ajoute WHERE hotel_id = ?).
        ReservationCreateDto updateDto = new ReservationCreateDto(
                clientFrId, null,
                created.dateArrivee(), created.dateDepart(),
                1, 0, null, null, BigDecimal.ZERO,
                List.of(new ReservationChambreCreateDto(
                        chambreMrId, null, null, new BigDecimal("80.00"))),
                null);

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> transactionTemplate.execute(s ->
                        reservationService.update(resId, updateDto)));
        assertEquals("error.client.notFound", ex.getMessage(),
                "Le clientRepository.findById() doit renvoyer empty -> notFound");
    }
}

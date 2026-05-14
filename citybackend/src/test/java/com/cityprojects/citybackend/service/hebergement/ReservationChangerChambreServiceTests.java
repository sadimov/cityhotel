package com.cityprojects.citybackend.service.hebergement;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.dto.hebergement.ChangerChambreRequest;
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
import com.cityprojects.citybackend.repository.client.ClientRepository;
import com.cityprojects.citybackend.repository.core.DBUserRepository;
import com.cityprojects.citybackend.repository.core.HotelRepository;
import com.cityprojects.citybackend.repository.core.RoleRepository;
import com.cityprojects.citybackend.repository.hebergement.ChambreRepository;
import com.cityprojects.citybackend.repository.hebergement.NuiteeRepository;
import com.cityprojects.citybackend.repository.hebergement.ReservationChambreRepository;
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
 * Tests Surefire (rapides, en H2) du flow changerChambre() - Tour 44 Phase 1.
 *
 * <h3>Couverture</h3>
 * <ol>
 *   <li>T1 : happy path - changement chambre sans conflit -&gt; pivot mis a
 *       jour, nuitees PREVUES rebasculees sur la nouvelle chambre.</li>
 *   <li>T2 : conflit - reservation B existe sur la nouvelle chambre dans la
 *       periode -&gt; BusinessException error.reservation.chambre.conflict.</li>
 * </ol>
 */
@SpringBootTest
@ActiveProfiles("test")
class ReservationChangerChambreServiceTests {

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private HotelRepository hotelRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private DBUserRepository userRepository;

    @Autowired
    private TypeChambreRepository typeChambreRepository;

    @Autowired
    private ChambreRepository chambreRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private ReservationChambreRepository reservationChambreRepository;

    @Autowired
    private NuiteeRepository nuiteeRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;
    private Long hotelMrId;
    private Long userMrId;
    private Long chambre101Id;
    private Long chambre102Id;
    private Long clientId;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        TenantContext.clear();
        SecurityContextHolder.clearContext();

        // Cleanup ordonne (FK) - inclut tables finance (Tour 44 chaine atomique create)
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
        jdbcTemplate.update("DELETE FROM finance.numerotation_sequence");
        jdbcTemplate.update("DELETE FROM core.dbusers");
        jdbcTemplate.update("DELETE FROM core.hotels");
        jdbcTemplate.update("DELETE FROM core.roles");

        Hotel mr = new Hotel("MRH001", "Hotel Mauritanie");
        mr.setCodePays("MR");
        hotelMrId = hotelRepository.saveAndFlush(mr).getHotelId();
        Role gerant = roleRepository.saveAndFlush(new Role("GERANT", "Gerant"));
        DBUser user = new DBUser("recept1", "r1@h.test",
                "$2a$12$placeholderhashplaceholderhashplaceholderhashplacehash",
                "Mariam", "Sow", mr, gerant);
        user.setActif(Boolean.TRUE);
        user.setCompteVerrouille(Boolean.FALSE);
        userMrId = userRepository.saveAndFlush(user).getUserId();

        try {
            TenantContext.set(hotelMrId);
            TypeChambre type = transactionTemplate.execute(s -> {
                TypeChambre t = new TypeChambre();
                t.setTypeCode("STD"); t.setTypeNom("Standard");
                t.setNbLitsMax(2); t.setNbPersonnesMax(2); t.setActif(Boolean.TRUE);
                return typeChambreRepository.save(t);
            });
            Chambre c101 = transactionTemplate.execute(s -> {
                Chambre c = new Chambre();
                c.setNumeroChambre("101"); c.setTypeId(type.getTypeId());
                c.setStatut(StatutChambre.DISPONIBLE); c.setNbLits(1); c.setNbPersonnesMax(2);
                c.setActif(Boolean.TRUE);
                return chambreRepository.save(c);
            });
            chambre101Id = c101.getChambreId();
            Chambre c102 = transactionTemplate.execute(s -> {
                Chambre c = new Chambre();
                c.setNumeroChambre("102"); c.setTypeId(type.getTypeId());
                c.setStatut(StatutChambre.DISPONIBLE); c.setNbLits(1); c.setNbPersonnesMax(2);
                c.setActif(Boolean.TRUE);
                return chambreRepository.save(c);
            });
            chambre102Id = c102.getChambreId();
            Client client = transactionTemplate.execute(s -> {
                Client cl = new Client();
                cl.setNumeroClient("CLI-2026-MR-000051");
                cl.setPrenom("Aicha"); cl.setNom("Diallo"); cl.setActif(Boolean.TRUE);
                return clientRepository.save(cl);
            });
            clientId = client.getClientId();
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
        jdbcTemplate.update("DELETE FROM finance.numerotation_sequence");
        jdbcTemplate.update("DELETE FROM core.dbusers");
        jdbcTemplate.update("DELETE FROM core.hotels");
        jdbcTemplate.update("DELETE FROM core.roles");
    }

    private void auth() {
        UserPrincipal p = new UserPrincipal(userMrId, "u", "u@h.test", "x",
                "T", "U", hotelMrId, "MRH001", "Hotel MR",
                "GERANT", "GERANT", true, false,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_GERANT")));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities()));
    }

    private Long createReservation(Long chambreId, LocalDate arr, LocalDate dep) {
        ReservationCreateDto dto = new ReservationCreateDto(
                clientId, null, arr, dep, 1, 0, null, null, BigDecimal.ZERO,
                List.of(new ReservationChambreCreateDto(chambreId, null, null, new BigDecimal("100.00"))),
                null);
        return transactionTemplate.execute(s -> reservationService.create(dto)).reservationId();
    }

    @Test
    @DisplayName("T1 - changerChambre() happy path : pivot et nuitees PREVUES rebasculees")
    void shouldChangeChambreHappyPath() {
        TenantContext.set(hotelMrId);
        auth();
        LocalDate arr = LocalDate.now().plusDays(5);
        LocalDate dep = arr.plusDays(2);
        Long resId = createReservation(chambre101Id, arr, dep);

        // Changement 101 -> 102
        ReservationDto updated = transactionTemplate.execute(s -> reservationService.changerChambre(
                resId, new ChangerChambreRequest(chambre101Id, chambre102Id, "Demande client")));

        assertNotNull(updated);
        // Le pivot doit pointer sur 102
        try {
            TenantContext.set(hotelMrId);
            var pivots = transactionTemplate.execute(s ->
                    reservationChambreRepository.findByReservationIdOrderByDateDebutAsc(resId));
            assertEquals(1, pivots.size());
            assertEquals(chambre102Id, pivots.get(0).getChambreId(),
                    "Pivot doit referencer la nouvelle chambre");

            // Les nuitees PREVUES doivent toutes etre sur 102 maintenant
            var nuitees = transactionTemplate.execute(s ->
                    nuiteeRepository.findByReservationIdOrderByDateNuitAsc(resId));
            assertEquals(2, nuitees.size());
            for (var n : nuitees) {
                assertEquals(chambre102Id, n.getChambreId(),
                        "Nuitee PREVUE doit etre rebasculee sur la nouvelle chambre");
            }
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("T2 - conflit sur la nouvelle chambre -> BusinessException")
    void shouldRejectConflictOnNewChambre() {
        TenantContext.set(hotelMrId);
        auth();
        LocalDate arr = LocalDate.now().plusDays(5);
        LocalDate dep = arr.plusDays(2);
        // Resa 1 sur 101
        Long resA = createReservation(chambre101Id, arr, dep);
        // Resa 2 sur 102 sur la MEME periode (bloquante pour le swap)
        createReservation(chambre102Id, arr, dep);

        assertThrows(BusinessException.class,
                () -> transactionTemplate.execute(s -> reservationService.changerChambre(
                        resA, new ChangerChambreRequest(chambre101Id, chambre102Id, "Tentative bloquee"))));
    }
}

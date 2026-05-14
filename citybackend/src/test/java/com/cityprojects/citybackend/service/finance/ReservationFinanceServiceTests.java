package com.cityprojects.citybackend.service.finance;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.dto.finance.RecapPaiementsReservationDto;
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

/**
 * Tests Surefire (rapides, en H2) du {@link ReservationFinanceService} -
 * Tour 44 Phase 1.
 *
 * <h3>Couverture</h3>
 * <ol>
 *   <li>T1 : getRecapForReservation() apres create() -&gt; 1 facture
 *       previsionnelle, total = somme nuitees, paye = 0, reste = total.</li>
 *   <li>T2 : multi-tenant - recap depuis l'hotel B -&gt; ResourceNotFoundException.</li>
 * </ol>
 */
@SpringBootTest
@ActiveProfiles("test")
class ReservationFinanceServiceTests {

    @Autowired
    private ReservationFinanceService reservationFinanceService;

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
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;
    private Long hotelMrId;
    private Long hotelFrId;
    private Long userMrId;
    private Long chambreMrId;
    private Long clientMrId;

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

        Hotel fr = new Hotel("FRH001", "Hotel France");
        fr.setCodePays("FR");
        hotelFrId = hotelRepository.saveAndFlush(fr).getHotelId();

        Role gerant = roleRepository.saveAndFlush(new Role("GERANT", "Gerant"));

        DBUser user = new DBUser("recept1", "recept1@h.test",
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
                cl.setNumeroClient("CLI-2026-MR-000050");
                cl.setPrenom("Aicha");
                cl.setNom("Diallo");
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
    @DisplayName("T1 - getRecapForReservation() apres create -> 1 facture previsionnelle, paye=0, reste=total")
    void shouldReturnRecapAfterCreate() {
        TenantContext.set(hotelMrId);
        authenticateAs(userMrId, hotelMrId, "GERANT");

        // 2 nuits a 100 = 200
        LocalDate arrivee = LocalDate.now().plusDays(1);
        LocalDate depart = arrivee.plusDays(2);
        ReservationCreateDto dto = new ReservationCreateDto(
                clientMrId, null, arrivee, depart, 1, 0, null, null, BigDecimal.ZERO,
                List.of(new ReservationChambreCreateDto(chambreMrId, null, null, new BigDecimal("100.00"))),
                null);
        ReservationDto created = transactionTemplate.execute(s -> reservationService.create(dto));

        RecapPaiementsReservationDto recap = transactionTemplate.execute(s ->
                reservationFinanceService.getRecapForReservation(created.reservationId()));

        assertNotNull(recap);
        assertEquals(created.reservationId(), recap.reservationId());
        assertEquals(1, recap.factures().size(), "1 facture previsionnelle generee par create()");
        var f = recap.factures().get(0);
        assertEquals(0, f.montantTotal().compareTo(new BigDecimal("200.00")));
        assertEquals(0, f.montantPaye().compareTo(BigDecimal.ZERO));
        assertEquals(0, f.reste().compareTo(new BigDecimal("200.00")));
        assertEquals(0, recap.totalGlobal().compareTo(new BigDecimal("200.00")));
        assertEquals(0, recap.payeGlobal().compareTo(BigDecimal.ZERO));
        assertEquals(0, recap.resteGlobal().compareTo(new BigDecimal("200.00")));
        assertFalse(recap.paiements().size() > 0, "Aucun paiement encore enregistre");
    }

    @Test
    @DisplayName("T2 - Multi-tenant : reservation d'hotel MR invisible depuis hotel FR")
    void shouldIsolateAcrossTenants() {
        TenantContext.set(hotelMrId);
        authenticateAs(userMrId, hotelMrId, "GERANT");
        LocalDate arrivee = LocalDate.now().plusDays(1);
        LocalDate depart = arrivee.plusDays(1);
        ReservationCreateDto dto = new ReservationCreateDto(
                clientMrId, null, arrivee, depart, 1, 0, null, null, BigDecimal.ZERO,
                List.of(new ReservationChambreCreateDto(chambreMrId, null, null, new BigDecimal("100.00"))),
                null);
        ReservationDto created = transactionTemplate.execute(s -> reservationService.create(dto));

        // Bascule hotel FR
        TenantContext.set(hotelFrId);
        assertThrows(ResourceNotFoundException.class,
                () -> transactionTemplate.execute(s ->
                        reservationFinanceService.getRecapForReservation(created.reservationId())));
    }
}

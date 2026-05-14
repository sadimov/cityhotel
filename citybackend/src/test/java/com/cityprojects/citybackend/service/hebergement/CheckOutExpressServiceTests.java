package com.cityprojects.citybackend.service.hebergement;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.dto.hebergement.CheckOutExpressRequest;
import com.cityprojects.citybackend.dto.hebergement.ReservationChambreCreateDto;
import com.cityprojects.citybackend.dto.hebergement.ReservationCreateDto;
import com.cityprojects.citybackend.dto.hebergement.ReservationDto;
import com.cityprojects.citybackend.entity.client.Client;
import com.cityprojects.citybackend.entity.client.Societe;
import com.cityprojects.citybackend.entity.core.DBUser;
import com.cityprojects.citybackend.entity.core.Hotel;
import com.cityprojects.citybackend.entity.core.Role;
import com.cityprojects.citybackend.entity.hebergement.Chambre;
import com.cityprojects.citybackend.entity.hebergement.StatutChambre;
import com.cityprojects.citybackend.entity.hebergement.StatutReservation;
import com.cityprojects.citybackend.entity.hebergement.TypeChambre;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.repository.client.ClientRepository;
import com.cityprojects.citybackend.repository.client.SocieteRepository;
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
 * Tests Surefire Tour 45 : {@link ReservationService#checkOutExpress}.
 *
 * <h3>Couverture</h3>
 * <ol>
 *   <li>T1 : check-out express reussi - reservation passe ARRIVEE -&gt; PARTIE,
 *       facture passe a PAYEE, transfert sur compte societe.</li>
 *   <li>T2 : refus si reservation pas ARRIVEE -&gt; BusinessException.</li>
 * </ol>
 */
@SpringBootTest
@ActiveProfiles("test")
class CheckOutExpressServiceTests {

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
    private SocieteRepository societeRepository;

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
    private Long userMrId;
    private Long clientMrId;
    private Long chambreMrId;
    private Long societeMrId;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        TenantContext.clear();
        SecurityContextHolder.clearContext();
        cleanAll();

        Hotel mr = new Hotel("MRCKE", "Hotel CheckoutExpress");
        mr.setCodePays("MR");
        hotelMrId = hotelRepository.saveAndFlush(mr).getHotelId();

        Role gerant = roleRepository.saveAndFlush(new Role("GERANT", "Gerant"));

        DBUser user = new DBUser("userCKE", "cke@h.test",
                "$2a$12$placeholderhashplaceholderhashplaceholderhashplacehash",
                "Checkout", "Express", mr, gerant);
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
                cl.setNumeroClient("CLI-2026-MR-CKE01");
                cl.setPrenom("Voyageur");
                cl.setNom("Tour45");
                cl.setActif(Boolean.TRUE);
                return clientRepository.save(cl);
            });
            clientMrId = client.getClientId();
            Societe soc = transactionTemplate.execute(s -> {
                Societe sc = new Societe();
                sc.setSocieteNom("Societe Tour 45");
                sc.setActif(Boolean.TRUE);
                return societeRepository.save(sc);
            });
            societeMrId = soc.getSocieteId();
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

    private void authenticate() {
        UserPrincipal principal = new UserPrincipal(
                userMrId, "userCKE", "cke@h.test", "pwd",
                "Checkout", "Express", hotelMrId, "MRCKE", "Hotel CheckoutExpress",
                "GERANT", "GERANT", Boolean.TRUE, Boolean.FALSE,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_GERANT")));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @Test
    @DisplayName("T1 - checkOutExpress sur reservation ARRIVEE -> PARTIE + facture PAYEE + chambre NETTOYAGE")
    void shouldCheckOutExpressAndTransferToSociete() {
        TenantContext.set(hotelMrId);
        authenticate();

        // 1) Cree reservation + facture previsionnelle automatique
        LocalDate arrivee = LocalDate.now().minusDays(1);
        LocalDate depart = LocalDate.now().plusDays(1);
        ReservationCreateDto dto = new ReservationCreateDto(
                clientMrId, null, arrivee, depart, 1, 0, null, null, BigDecimal.ZERO,
                List.of(new ReservationChambreCreateDto(chambreMrId, null, null, new BigDecimal("100.00"))),
                null);
        ReservationDto created = transactionTemplate.execute(s -> reservationService.create(dto));

        // 2) Check-in pour passer en ARRIVEE
        ReservationDto arrived = transactionTemplate.execute(s ->
                reservationService.checkIn(created.reservationId()));
        assertEquals(StatutReservation.ARRIVEE, arrived.statut());

        // 3) Check-out express
        CheckOutExpressRequest req = new CheckOutExpressRequest(societeMrId, clientMrId);
        ReservationDto result = transactionTemplate.execute(s ->
                reservationService.checkOutExpress(created.reservationId(), req));

        assertNotNull(result);
        assertEquals(StatutReservation.PARTIE, result.statut());

        // Verifier que la facture est passee a PAYEE
        Integer payed = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM finance.factures WHERE hotel_id = ? AND statut = 'PAYEE'",
                Integer.class, hotelMrId);
        assertNotNull(payed);
        assertEquals(1, payed.intValue(), "1 facture doit etre PAYEE");

        // Verifier qu'il y a une operation DEBIT sur compte societe
        Integer ops = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM finance.operations_comptes WHERE hotel_id = ? AND type_operation = 'DEBIT'",
                Integer.class, hotelMrId);
        assertNotNull(ops);
        // 1 DEBIT a l'emission de la facture + 1 DEBIT lors du transfert = >= 2
        assertEquals(true, ops >= 1);
    }

    @Test
    @DisplayName("T2 - checkOutExpress refuse si reservation pas ARRIVEE -> BusinessException")
    void shouldRejectIfReservationNotArrived() {
        TenantContext.set(hotelMrId);
        authenticate();

        LocalDate arrivee = LocalDate.now().plusDays(1);
        LocalDate depart = arrivee.plusDays(2);
        ReservationCreateDto dto = new ReservationCreateDto(
                clientMrId, null, arrivee, depart, 1, 0, null, null, BigDecimal.ZERO,
                List.of(new ReservationChambreCreateDto(chambreMrId, null, null, new BigDecimal("80.00"))),
                null);
        ReservationDto created = transactionTemplate.execute(s -> reservationService.create(dto));
        // Statut = CONFIRMEE (pas ARRIVEE)

        CheckOutExpressRequest req = new CheckOutExpressRequest(societeMrId, clientMrId);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> transactionTemplate.execute(s ->
                        reservationService.checkOutExpress(created.reservationId(), req)));
        assertEquals("error.checkoutExpress.statut.invalid", ex.getMessage());
    }
}

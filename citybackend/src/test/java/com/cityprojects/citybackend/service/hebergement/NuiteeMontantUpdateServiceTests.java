package com.cityprojects.citybackend.service.hebergement;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.dto.hebergement.NuiteeModificationDto;
import com.cityprojects.citybackend.dto.hebergement.NuiteeMontantUpdateRequest;
import com.cityprojects.citybackend.dto.hebergement.NuiteesUpdateResultDto;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests Surefire Tour 45 : {@link NuiteeService#findProvisoiresByReservation(Long)}
 * et {@link NuiteeService#updateMontants(List)}.
 *
 * <h3>Couverture</h3>
 * <ol>
 *   <li>T1 : findProvisoiresByReservation() retourne les nuitees avec ligneFactureId
 *       et statutFactureParente=EMISE apres creation reservation +
 *       previsionFromReservation.</li>
 *   <li>T2 : updateMontants() succeede, le prix nuitee + ligneFacture sont
 *       mis a jour, la facture est recalculee, totalImpact correct.</li>
 *   <li>T3 : updateMontants() refuse si facture parente PAYEE -&gt; cle i18n
 *       {@code error.nuitee.facture.payee}.</li>
 * </ol>
 */
@SpringBootTest
@ActiveProfiles("test")
class NuiteeMontantUpdateServiceTests {

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
    private Long userMrId;
    private Long clientMrId;
    private Long chambreMrId;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        TenantContext.clear();
        SecurityContextHolder.clearContext();
        cleanAll();

        Hotel mr = new Hotel("MRH045", "Hotel Tour 45");
        mr.setCodePays("MR");
        hotelMrId = hotelRepository.saveAndFlush(mr).getHotelId();

        Role gerant = roleRepository.saveAndFlush(new Role("GERANT", "Gerant"));

        DBUser user = new DBUser("user45", "user45@h.test",
                "$2a$12$placeholderhashplaceholderhashplaceholderhashplacehash",
                "Sidi", "Tour45", mr, gerant);
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
                cl.setNumeroClient("CLI-2026-MR-T45001");
                cl.setPrenom("Test");
                cl.setNom("Client");
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
                userMrId, "user45", "user45@h.test", "pwd",
                "Sidi", "Tour45", hotelMrId, "MRH045", "Hotel Tour 45",
                "GERANT", "GERANT", Boolean.TRUE, Boolean.FALSE,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_GERANT")));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private ReservationDto creerReservationAvecFacture() {
        LocalDate arrivee = LocalDate.now().plusDays(1);
        LocalDate depart = arrivee.plusDays(2); // 2 nuits
        ReservationCreateDto dto = new ReservationCreateDto(
                clientMrId, null, arrivee, depart, 1, 0, null, null, BigDecimal.ZERO,
                List.of(new ReservationChambreCreateDto(chambreMrId, null, null, new BigDecimal("100.00"))),
                null);
        // create() pousse automatiquement previsionFromReservation -> facture EMISE
        return transactionTemplate.execute(s -> reservationService.create(dto));
    }

    @Test
    @DisplayName("T1 - findProvisoires retourne les nuitees avec statutFactureParente=EMISE")
    void shouldReturnProvisoiresWithStatutEmise() {
        TenantContext.set(hotelMrId);
        authenticate();

        ReservationDto reservation = creerReservationAvecFacture();

        List<NuiteeModificationDto> provisoires = transactionTemplate.execute(s ->
                nuiteeService.findProvisoiresByReservation(reservation.reservationId()));

        assertNotNull(provisoires);
        assertEquals(2, provisoires.size(), "2 nuits doivent etre eligibles");
        NuiteeModificationDto first = provisoires.get(0);
        assertEquals(0, first.prixOriginal().compareTo(new BigDecimal("100.00")));
        assertNotNull(first.ligneFactureId(), "ligneFactureId doit etre renseigne (facture previsionnelle)");
        assertEquals("EMISE", first.statutFactureParente());
        assertEquals(0, first.montantLigneFacture().compareTo(new BigDecimal("100.00")));
    }

    @Test
    @DisplayName("T2 - updateMontants applique les nouveaux montants, recalcule facture, totalImpact correct")
    void shouldUpdateMontantsAndRecomputeFacture() {
        TenantContext.set(hotelMrId);
        authenticate();

        ReservationDto reservation = creerReservationAvecFacture();
        List<NuiteeModificationDto> provisoires = transactionTemplate.execute(s ->
                nuiteeService.findProvisoiresByReservation(reservation.reservationId()));
        assertEquals(2, provisoires.size());

        // Modifier les deux nuits a 150 (au lieu de 100). delta = 2 * 50 = 100
        List<NuiteeMontantUpdateRequest> requests = List.of(
                new NuiteeMontantUpdateRequest(
                        provisoires.get(0).nuiteeId(), new BigDecimal("150.00"),
                        provisoires.get(0).ligneFactureId(), null),
                new NuiteeMontantUpdateRequest(
                        provisoires.get(1).nuiteeId(), new BigDecimal("150.00"),
                        provisoires.get(1).ligneFactureId(), null));

        NuiteesUpdateResultDto result = transactionTemplate.execute(s ->
                nuiteeService.updateMontants(requests));

        assertEquals(2, result.updatedCount());
        assertEquals(0, result.totalImpact().compareTo(new BigDecimal("100.00")),
                "totalImpact = 2 nuits * (150 - 100) = 100");

        // Verifier que la facture a ete recalculee : 2 * 150 = 300
        // Lecture via les nouvelles provisoires (la facture est toujours EMISE,
        // donc les nuitees restent provisoires).
        List<NuiteeModificationDto> apres = transactionTemplate.execute(s ->
                nuiteeService.findProvisoiresByReservation(reservation.reservationId()));
        assertEquals(2, apres.size());
        assertEquals(0, apres.get(0).prixOriginal().compareTo(new BigDecimal("150.00")));
        assertEquals(0, apres.get(0).montantLigneFacture().compareTo(new BigDecimal("150.00")));
    }

    @Test
    @DisplayName("T3 - updateMontants refuse si la facture parente est PAYEE -> error.nuitee.facture.payee")
    void shouldRejectIfFacturePayee() {
        TenantContext.set(hotelMrId);
        authenticate();

        ReservationDto reservation = creerReservationAvecFacture();
        List<NuiteeModificationDto> provisoires = transactionTemplate.execute(s ->
                nuiteeService.findProvisoiresByReservation(reservation.reservationId()));
        assertEquals(2, provisoires.size());

        // Force le statut a PAYEE via JDBC (court-circuit le workflow normal pour ce test)
        jdbcTemplate.update("UPDATE finance.factures SET statut = ? WHERE hotel_id = ?",
                "PAYEE", hotelMrId);

        // Recharge la liste : aucune nuitee n'est plus provisoire
        List<NuiteeModificationDto> apresPayement = transactionTemplate.execute(s ->
                nuiteeService.findProvisoiresByReservation(reservation.reservationId()));
        assertTrue(apresPayement.isEmpty(),
                "Une fois la facture PAYEE, aucune nuitee n'est plus provisoire");

        // Tente quand meme de modifier -> BusinessException
        List<NuiteeMontantUpdateRequest> requests = List.of(
                new NuiteeMontantUpdateRequest(
                        provisoires.get(0).nuiteeId(), new BigDecimal("200.00"),
                        provisoires.get(0).ligneFactureId(), null));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> transactionTemplate.execute(s -> nuiteeService.updateMontants(requests)));
        assertEquals("error.nuitee.facture.payee", ex.getMessage());
    }
}

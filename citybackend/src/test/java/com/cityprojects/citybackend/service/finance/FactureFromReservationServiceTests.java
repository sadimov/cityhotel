package com.cityprojects.citybackend.service.finance;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.dto.finance.FactureDto;
import com.cityprojects.citybackend.dto.hebergement.NuiteeDto;
import com.cityprojects.citybackend.dto.hebergement.ReservationChambreCreateDto;
import com.cityprojects.citybackend.dto.hebergement.ReservationCreateDto;
import com.cityprojects.citybackend.dto.hebergement.ReservationDto;
import com.cityprojects.citybackend.entity.client.Client;
import com.cityprojects.citybackend.entity.core.DBUser;
import com.cityprojects.citybackend.entity.core.Hotel;
import com.cityprojects.citybackend.entity.core.Role;
import com.cityprojects.citybackend.entity.finance.StatutFacture;
import com.cityprojects.citybackend.entity.finance.TypeLigneFacture;
import com.cityprojects.citybackend.entity.hebergement.Chambre;
import com.cityprojects.citybackend.entity.hebergement.StatutChambre;
import com.cityprojects.citybackend.entity.hebergement.StatutNuitee;
import com.cityprojects.citybackend.entity.hebergement.TypeChambre;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.repository.client.ClientRepository;
import com.cityprojects.citybackend.repository.core.DBUserRepository;
import com.cityprojects.citybackend.repository.core.HotelRepository;
import com.cityprojects.citybackend.repository.core.RoleRepository;
import com.cityprojects.citybackend.repository.hebergement.ChambreRepository;
import com.cityprojects.citybackend.repository.hebergement.NuiteeRepository;
import com.cityprojects.citybackend.repository.hebergement.ReservationRepository;
import com.cityprojects.citybackend.repository.hebergement.TypeChambreRepository;
import com.cityprojects.citybackend.security.UserPrincipal;
import com.cityprojects.citybackend.service.hebergement.NuiteeService;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests Surefire cross-module : {@link FactureService#fromReservation(Long)}.
 *
 * <h3>Couverture (vigilance comptable)</h3>
 * <ol>
 *   <li>T1 : reservation avec 3 nuitees CONSOMMEE -&gt; cree 1 facture EMISE
 *       avec 3 lignes NUITEE, met a jour Reservation.factureId, Nuitee.factureId,
 *       Nuitee.ligneFactureId et transition CONSOMMEE -&gt; FACTUREE pour les 3.
 *       Total = 3 * prixNuit.</li>
 *   <li>T2 : Idempotence - 2eme appel sur meme reservation -&gt; BusinessException
 *       (dejaFacturee).</li>
 * </ol>
 */
@SpringBootTest
@ActiveProfiles("test")
class FactureFromReservationServiceTests {

    @Autowired
    private FactureService factureService;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private NuiteeService nuiteeService;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private NuiteeRepository nuiteeRepository;

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

        Hotel mr = new Hotel("MRH001", "Hotel Mauritanie");
        mr.setCodePays("MR");
        hotelMrId = hotelRepository.saveAndFlush(mr).getHotelId();

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
                cl.setNumeroClient("CLI-2026-MR-000077");
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
        jdbcTemplate.update("DELETE FROM finance.affectations_paiements");
        jdbcTemplate.update("DELETE FROM finance.operations_comptes");
        jdbcTemplate.update("DELETE FROM finance.paiements");
        jdbcTemplate.update("DELETE FROM finance.lignes_factures");
        // Avant les factures car nuitees ont FK -> factures
        jdbcTemplate.update("UPDATE hebergement.nuitees SET facture_id = NULL, ligne_facture_id = NULL");
        jdbcTemplate.update("UPDATE hebergement.reservations SET facture_id = NULL");
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
                userMrId, "test-user", "test@h.test", "pwd",
                "Test", "User", hotelMrId, "MRH001", "Hotel Test",
                "GERANT", "GERANT", Boolean.TRUE, Boolean.FALSE,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_GERANT")));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    /**
     * Cree une reservation 3 nuits puis transitionne ses nuitees en CONSOMMEE
     * via update direct (le workflow check-in/check-out passe par
     * NightAuditService que l'on n'invoque pas ici - test cible la facturation).
     */
    private Long createReservationWithConsommees(BigDecimal prixNuit) {
        LocalDate arrivee = LocalDate.now().plusDays(1);
        LocalDate depart = arrivee.plusDays(3);  // 3 nuits
        ReservationCreateDto dto = new ReservationCreateDto(
                clientMrId, null, arrivee, depart, 1, 0, null, null, BigDecimal.ZERO,
                List.of(new ReservationChambreCreateDto(chambreMrId, null, null, prixNuit)),
                null);
        ReservationDto created = transactionTemplate.execute(s -> reservationService.create(dto));

        // Bascule les 3 nuitees en CONSOMMEE via JPA (simule check-in + nuit passee)
        List<NuiteeDto> nuitees = transactionTemplate.execute(s ->
                nuiteeService.findByReservation(created.reservationId()));
        for (NuiteeDto n : nuitees) {
            transactionTemplate.execute(s -> {
                var nuitee = nuiteeRepository.findById(n.id()).orElseThrow();
                nuitee.setStatut(StatutNuitee.CONSOMMEE);
                nuiteeRepository.save(nuitee);
                return null;
            });
        }
        return created.reservationId();
    }

    @Test
    @DisplayName("T1 - fromReservation() avec 3 nuitees CONSOMMEE -> facture EMISE 3 lignes, statut FACTUREE, FK propagees")
    void shouldCreateFactureFromReservation() {
        TenantContext.set(hotelMrId);
        authenticate();

        BigDecimal prixNuit = new BigDecimal("100.00");
        Long resId = createReservationWithConsommees(prixNuit);

        FactureDto facture = transactionTemplate.execute(s -> factureService.fromReservation(resId));

        assertNotNull(facture);
        assertEquals(StatutFacture.EMISE, facture.statut(),
                "fromReservation termine en EMISE (workflow check-out)");
        assertEquals(3, facture.lignes().size(), "1 ligne par nuitee");
        for (var ligne : facture.lignes()) {
            assertEquals(TypeLigneFacture.NUITEE, ligne.typeLigne());
            assertNotNull(ligne.nuiteeId());
            assertEquals(0, ligne.prixUnitaire().compareTo(prixNuit));
        }
        // Total = 3 * 100 = 300
        assertEquals(0, facture.montantTtc().compareTo(new BigDecimal("300.00")));
        assertEquals(resId, facture.reservationId());

        // La reservation doit avoir factureId set
        var resApres = transactionTemplate.execute(s -> reservationRepository.findById(resId).orElseThrow());
        assertEquals(facture.factureId(), resApres.getFactureId(),
                "Reservation.factureId doit etre renseigne");

        // Les 3 nuitees doivent etre FACTUREE avec factureId + ligneFactureId
        List<NuiteeDto> nuiteesApres = transactionTemplate.execute(s ->
                nuiteeService.findByReservation(resId));
        for (NuiteeDto n : nuiteesApres) {
            var nuitee = transactionTemplate.execute(s -> nuiteeRepository.findById(n.id()).orElseThrow());
            assertEquals(StatutNuitee.FACTUREE, nuitee.getStatut(),
                    "Nuitee transitionnee CONSOMMEE -> FACTUREE");
            assertEquals(facture.factureId(), nuitee.getFactureId(),
                    "Nuitee.factureId set");
            assertNotNull(nuitee.getLigneFactureId(),
                    "Nuitee.ligneFactureId set");
        }
    }

    @Test
    @DisplayName("T2 - Idempotence : 2eme appel sur meme reservation deja facturee -> BusinessException")
    void shouldRejectDuplicateFromReservation() {
        TenantContext.set(hotelMrId);
        authenticate();

        Long resId = createReservationWithConsommees(new BigDecimal("100.00"));

        // 1er appel : OK
        FactureDto facture = transactionTemplate.execute(s -> factureService.fromReservation(resId));
        assertNotNull(facture);

        // 2eme appel : doit echouer
        BusinessException ex = assertThrows(BusinessException.class, () ->
                transactionTemplate.execute(s -> factureService.fromReservation(resId)));
        assertTrue(ex.getMessage().contains("dejaFacturee")
                        || ex.getMessage().contains("error.facture"),
                "Doit lever error.facture.reservation.dejaFacturee");
    }
}

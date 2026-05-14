package com.cityprojects.citybackend.service.finance;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.dto.finance.FolioDto;
import com.cityprojects.citybackend.entity.client.Client;
import com.cityprojects.citybackend.entity.core.DBUser;
import com.cityprojects.citybackend.entity.core.Hotel;
import com.cityprojects.citybackend.entity.core.Role;
import com.cityprojects.citybackend.entity.finance.Compte;
import com.cityprojects.citybackend.exception.ResourceNotFoundException;
import com.cityprojects.citybackend.repository.client.ClientRepository;
import com.cityprojects.citybackend.repository.core.DBUserRepository;
import com.cityprojects.citybackend.repository.core.HotelRepository;
import com.cityprojects.citybackend.repository.core.RoleRepository;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests Surefire Tour 46 : {@link OperationCompteService#findFolio}.
 *
 * <h3>Couverture</h3>
 * <ol>
 *   <li>T1 - folio sur un client inexistant -&gt; {@link ResourceNotFoundException}.</li>
 *   <li>T2 - folio sur un compte vide (client sans operations) -&gt; soldes a 0, liste vide.</li>
 *   <li>T3 - folio avec DEBIT 5000 + CREDIT 2000 dans la plage -&gt; soldes coherents,
 *       totalDebits=5000, totalCredits=2000, soldeCloture=3000.</li>
 *   <li>T4 - folio filtre par dates : op anterieure incluse dans soldeOuverture,
 *       op dans la plage listee, op posterieure ignoree.</li>
 *   <li>T5 - isolation multi-tenant : un client de l'hotel A n'est pas visible
 *       depuis le tenant B -&gt; {@link ResourceNotFoundException}.</li>
 * </ol>
 */
@SpringBootTest
@ActiveProfiles("test")
class FolioServiceTests {

    @Autowired
    private CompteService compteService;

    @Autowired
    private OperationCompteService operationCompteService;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private HotelRepository hotelRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private DBUserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;
    private Long hotelMrId;
    private Long hotelFrId;
    private DBUser userMr;
    private Long clientMrId;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        TenantContext.clear();
        SecurityContextHolder.clearContext();
        cleanAll();

        Hotel mr = new Hotel("MR46F", "Hotel Tour46 MR");
        mr.setCodePays("MR");
        hotelMrId = hotelRepository.saveAndFlush(mr).getHotelId();

        Hotel fr = new Hotel("FR46F", "Hotel Tour46 FR");
        fr.setCodePays("FR");
        hotelFrId = hotelRepository.saveAndFlush(fr).getHotelId();

        Role gerant = roleRepository.saveAndFlush(new Role("GERANT", "Gerant"));
        userMr = new DBUser("u46f", "u46f@h.test",
                "$2a$12$placeholderhashplaceholderhashplaceholderhashplacehash",
                "Tour46", "Folio", mr, gerant);
        userMr.setActif(Boolean.TRUE);
        userMr.setCompteVerrouille(Boolean.FALSE);
        userMr = userRepository.saveAndFlush(userMr);

        try {
            TenantContext.set(hotelMrId);
            Client client = transactionTemplate.execute(s -> {
                Client cl = new Client();
                cl.setNumeroClient("CLI-2026-MR-T46F1");
                cl.setPrenom("Sidi");
                cl.setNom("Folio");
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
        jdbcTemplate.update("DELETE FROM client.clients");
        jdbcTemplate.update("DELETE FROM client.societes");
        jdbcTemplate.update("DELETE FROM finance.numerotation_sequence");
        jdbcTemplate.update("DELETE FROM core.dbusers");
        jdbcTemplate.update("DELETE FROM core.hotels");
        jdbcTemplate.update("DELETE FROM core.roles");
    }

    private void authenticate() {
        UserPrincipal principal = UserPrincipal.create(userMr,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_GERANT")));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @Test
    @DisplayName("T1 - findFolio() sur client inexistant -> ResourceNotFoundException")
    void shouldRejectFolioOnUnknownClient() {
        TenantContext.set(hotelMrId);
        authenticate();
        assertThrows(ResourceNotFoundException.class,
                () -> transactionTemplate.execute(s ->
                        operationCompteService.findFolio(99999L, null, null)));
    }

    @Test
    @DisplayName("T2 - findFolio() sur compte vide -> soldes a 0, liste vide")
    void shouldReturnEmptyFolioWhenNoOperations() {
        TenantContext.set(hotelMrId);
        authenticate();

        FolioDto folio = transactionTemplate.execute(s ->
                operationCompteService.findFolio(clientMrId, null, null));

        assertNotNull(folio);
        assertEquals(clientMrId, folio.clientId());
        assertEquals(0, folio.soldeOuverture().compareTo(BigDecimal.ZERO));
        assertEquals(0, folio.soldeCloture().compareTo(BigDecimal.ZERO));
        assertEquals(0, folio.totalDebits().compareTo(BigDecimal.ZERO));
        assertEquals(0, folio.totalCredits().compareTo(BigDecimal.ZERO));
        assertTrue(folio.operations().isEmpty());
        assertTrue(folio.clientNom() != null && folio.clientNom().contains("Folio"));
    }

    @Test
    @DisplayName("T3 - findFolio() avec DEBIT 5000 + CREDIT 2000 -> totalDebits=5000, totalCredits=2000, soldeCloture=3000")
    void shouldComputeBalancesAndAggregates() {
        TenantContext.set(hotelMrId);
        authenticate();

        Compte compte = transactionTemplate.execute(s -> compteService.findOrCreateForClient(clientMrId));
        transactionTemplate.execute(s ->
                operationCompteService.recordDebit(compte.getCompteId(),
                        new BigDecimal("5000.00"), 1L, "Facture FACT-2026-MR-000001"));
        transactionTemplate.execute(s ->
                operationCompteService.recordCredit(compte.getCompteId(),
                        new BigDecimal("2000.00"), 1L, "Paiement PAY-2026-MR-000001"));

        FolioDto folio = transactionTemplate.execute(s ->
                operationCompteService.findFolio(clientMrId, null, null));

        assertNotNull(folio);
        assertEquals(2, folio.operations().size(), "2 operations attendues");
        assertEquals(0, folio.soldeOuverture().compareTo(BigDecimal.ZERO),
                "Pas de borne basse -> ouverture = 0");
        assertEquals(0, folio.totalDebits().compareTo(new BigDecimal("5000.00")));
        assertEquals(0, folio.totalCredits().compareTo(new BigDecimal("2000.00")));
        assertEquals(0, folio.soldeCloture().compareTo(new BigDecimal("3000.00")),
                "0 + 5000 (DEBIT) - 2000 (CREDIT) = 3000");

        // Verifie l'enrichissement libelle / type
        assertTrue(folio.operations().stream().anyMatch(op -> "DEBIT".equals(op.type())));
        assertTrue(folio.operations().stream().anyMatch(op -> "CREDIT".equals(op.type())));
    }

    @Test
    @DisplayName("T4 - findFolio() filtre par dates : op futur exclue, ouverture coherente")
    void shouldFilterByDateRange() {
        TenantContext.set(hotelMrId);
        authenticate();

        Compte compte = transactionTemplate.execute(s -> compteService.findOrCreateForClient(clientMrId));
        // Pose 1 DEBIT + 1 CREDIT a "aujourd'hui" (les Instant.now() en seront tous tres proches).
        transactionTemplate.execute(s ->
                operationCompteService.recordDebit(compte.getCompteId(),
                        new BigDecimal("1000.00"), 1L, "DEBIT today"));
        transactionTemplate.execute(s ->
                operationCompteService.recordCredit(compte.getCompteId(),
                        new BigDecimal("400.00"), 1L, "CREDIT today"));

        // Filtre [today, today] : doit inclure les 2 ops.
        LocalDate today = LocalDate.now();
        FolioDto folioIn = transactionTemplate.execute(s ->
                operationCompteService.findFolio(clientMrId, today, today));
        assertEquals(2, folioIn.operations().size(), "Les 2 ops doivent etre incluses dans [today, today]");
        assertEquals(0, folioIn.soldeOuverture().compareTo(BigDecimal.ZERO),
                "Aucune op anterieure a today -> ouverture = 0");
        assertEquals(0, folioIn.soldeCloture().compareTo(new BigDecimal("600.00")),
                "1000 - 400 = 600");

        // Filtre [yesterday, yesterday] : aucune op ne doit y figurer.
        FolioDto folioBefore = transactionTemplate.execute(s ->
                operationCompteService.findFolio(clientMrId, today.minusDays(2), today.minusDays(1)));
        assertTrue(folioBefore.operations().isEmpty(),
                "Aucune op dans une plage anterieure");
        assertEquals(0, folioBefore.soldeOuverture().compareTo(BigDecimal.ZERO));
        assertEquals(0, folioBefore.soldeCloture().compareTo(BigDecimal.ZERO));

        // Filtre [tomorrow, tomorrow+1] : ops d'aujourd'hui doivent etre dans soldeOuverture.
        FolioDto folioAfter = transactionTemplate.execute(s ->
                operationCompteService.findFolio(clientMrId, today.plusDays(1), today.plusDays(2)));
        assertTrue(folioAfter.operations().isEmpty(),
                "Aucune op dans [tomorrow, tomorrow+1]");
        assertEquals(0, folioAfter.soldeOuverture().compareTo(new BigDecimal("600.00")),
                "Les 2 ops d'aujourd'hui doivent contribuer a l'ouverture de la plage future");
    }

    @Test
    @DisplayName("T5 - Isolation multi-tenant : client de hotel MR invisible depuis hotel FR")
    void shouldIsolateAcrossTenants() {
        // Pose 1 DEBIT pour clientMrId sur hotel MR.
        TenantContext.set(hotelMrId);
        authenticate();
        Compte compte = transactionTemplate.execute(s -> compteService.findOrCreateForClient(clientMrId));
        transactionTemplate.execute(s ->
                operationCompteService.recordDebit(compte.getCompteId(),
                        new BigDecimal("500.00"), 1L, "DEBIT MR"));
        Long clientId = clientMrId;
        TenantContext.clear();
        SecurityContextHolder.clearContext();

        // Authentifie un user FR puis tente d'acceder au folio du client MR -> 404.
        Role gerant = roleRepository.findByRoleCode("GERANT").orElseThrow();
        Hotel fr = hotelRepository.findById(hotelFrId).orElseThrow();
        DBUser userFr = new DBUser("u46fFR", "u46fFR@h.test",
                "$2a$12$placeholderhashplaceholderhashplaceholderhashplacehash",
                "Pierre", "FR", fr, gerant);
        userFr.setActif(Boolean.TRUE);
        userFr.setCompteVerrouille(Boolean.FALSE);
        DBUser persistedFr = userRepository.saveAndFlush(userFr);

        TenantContext.set(hotelFrId);
        UserPrincipal principal = UserPrincipal.create(persistedFr,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_GERANT")));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        assertThrows(ResourceNotFoundException.class,
                () -> transactionTemplate.execute(s ->
                        operationCompteService.findFolio(clientId, null, null)));
    }
}

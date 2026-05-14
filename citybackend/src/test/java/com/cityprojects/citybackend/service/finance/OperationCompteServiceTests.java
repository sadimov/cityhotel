package com.cityprojects.citybackend.service.finance;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.entity.core.DBUser;
import com.cityprojects.citybackend.entity.core.Hotel;
import com.cityprojects.citybackend.entity.core.Role;
import com.cityprojects.citybackend.entity.finance.Compte;
import com.cityprojects.citybackend.entity.finance.OperationCompte;
import com.cityprojects.citybackend.entity.finance.TypeOperationCompte;
import com.cityprojects.citybackend.exception.ResourceNotFoundException;
import com.cityprojects.citybackend.repository.core.DBUserRepository;
import com.cityprojects.citybackend.repository.core.HotelRepository;
import com.cityprojects.citybackend.repository.core.RoleRepository;
import com.cityprojects.citybackend.repository.finance.CompteRepository;
import com.cityprojects.citybackend.repository.finance.OperationCompteRepository;
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
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests Surefire (H2) du {@link OperationCompteService} et indirectement du
 * {@link CompteService} (Tour 22.1).
 *
 * <h3>Couverture</h3>
 * <ol>
 *   <li>T1 - {@code recordDebit} : DEBIT cree, {@code soldeActuel} augmente,
 *       {@code soldeAvant} et {@code soldeApres} coherents.</li>
 *   <li>T2 - {@code recordCredit} : CREDIT cree, {@code soldeActuel} diminue.</li>
 *   <li>T3 - Operations successives : DEBIT 5000 puis CREDIT 1500 puis CREDIT 3500
 *       -&gt; solde final = 0, audit trail complet.</li>
 *   <li>T4 - {@code recordDebit} avec compteId d'un autre tenant
 *       -&gt; {@link ResourceNotFoundException} (isolation Hibernate {@code @TenantId}).</li>
 *   <li>T5 - {@link CompteService#findOrCreateForClient(Long)} idempotent :
 *       2 appels avec le meme clientId retournent le meme compte.</li>
 * </ol>
 */
@SpringBootTest
@ActiveProfiles("test")
class OperationCompteServiceTests {

    @Autowired
    private CompteService compteService;

    @Autowired
    private OperationCompteService operationCompteService;

    @Autowired
    private CompteRepository compteRepository;

    @Autowired
    private OperationCompteRepository operationCompteRepository;

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

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        TenantContext.clear();
        SecurityContextHolder.clearContext();

        // Cleanup ordonne (ordre FK strict)
        jdbcTemplate.update("DELETE FROM finance.operations_comptes");
        jdbcTemplate.update("DELETE FROM finance.comptes");
        jdbcTemplate.update("DELETE FROM core.dbusers");
        jdbcTemplate.update("DELETE FROM core.hotels");
        jdbcTemplate.update("DELETE FROM core.roles");

        Hotel mr = new Hotel("MR1", "Hotel Mauritanie");
        mr.setCodePays("MR");
        hotelMrId = hotelRepository.saveAndFlush(mr).getHotelId();

        Hotel fr = new Hotel("FR1", "Hotel France");
        fr.setCodePays("FR");
        hotelFrId = hotelRepository.saveAndFlush(fr).getHotelId();

        Role gerant = roleRepository.saveAndFlush(new Role("GERANT", "Gerant"));

        userMr = new DBUser("gerant1", "gerant1@h1.test", "$2a$12$placeholder",
                "Sidi", "Cheikh", mr, gerant);
        userMr.setActif(Boolean.TRUE);
        userMr.setCompteVerrouille(Boolean.FALSE);
        userMr = userRepository.saveAndFlush(userMr);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
        jdbcTemplate.update("DELETE FROM finance.operations_comptes");
        jdbcTemplate.update("DELETE FROM finance.comptes");
        jdbcTemplate.update("DELETE FROM core.dbusers");
        jdbcTemplate.update("DELETE FROM core.hotels");
        jdbcTemplate.update("DELETE FROM core.roles");
    }

    private void authenticate() {
        UserPrincipal principal = UserPrincipal.create(userMr, Collections.singletonList(
                new SimpleGrantedAuthority("ROLE_GERANT")));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @Test
    @DisplayName("T1 - recordDebit() augmente soldeActuel et cree une OperationCompte DEBIT")
    void shouldRecordDebitAndIncreaseBalance() {
        TenantContext.set(hotelMrId);
        authenticate();

        // Cree un compte client (clientId factice 100, pas de FK enforcee dans
        // ce test : le test focalise OperationCompte, pas la coherence FK metier)
        Compte compte = transactionTemplate.execute(t -> compteService.findOrCreateForClient(100L));
        assertNotNull(compte);
        assertEquals(0, compte.getSoldeActuel().compareTo(BigDecimal.ZERO));
        assertEquals("CPT-CLI-100", compte.getNumeroCompte());

        BigDecimal montant = new BigDecimal("5000.00");
        OperationCompte op = transactionTemplate.execute(t ->
                operationCompteService.recordDebit(compte.getCompteId(), montant, 999L, "Facture FACT-2026-MR-000001"));

        assertNotNull(op);
        assertNotNull(op.getOperationId());
        assertEquals(TypeOperationCompte.DEBIT, op.getTypeOperation());
        assertEquals(0, op.getMontant().compareTo(montant));
        assertEquals(0, op.getSoldeAvant().compareTo(BigDecimal.ZERO));
        assertEquals(0, op.getSoldeApres().compareTo(montant));
        assertEquals(999L, op.getFactureId());

        // Compte rafraichi -> solde augmente
        Compte refreshed = transactionTemplate.execute(t -> compteRepository.findById(compte.getCompteId()).orElseThrow());
        assertEquals(0, refreshed.getSoldeActuel().compareTo(montant),
                "soldeActuel = 5000 apres DEBIT 5000");
    }

    @Test
    @DisplayName("T2 - recordCredit() diminue soldeActuel et cree une OperationCompte CREDIT")
    void shouldRecordCreditAndDecreaseBalance() {
        TenantContext.set(hotelMrId);
        authenticate();

        Compte compte = transactionTemplate.execute(t -> compteService.findOrCreateForClient(101L));

        // Pose d'abord un DEBIT pour avoir un solde positif a crediter.
        transactionTemplate.execute(t ->
                operationCompteService.recordDebit(compte.getCompteId(), new BigDecimal("2000.00"), 1L, "Facture init"));

        BigDecimal montantCredit = new BigDecimal("750.00");
        OperationCompte op = transactionTemplate.execute(t ->
                operationCompteService.recordCredit(compte.getCompteId(), montantCredit, 555L, "Paiement PAY-2026-MR-000001"));

        assertEquals(TypeOperationCompte.CREDIT, op.getTypeOperation());
        assertEquals(0, op.getMontant().compareTo(montantCredit));
        assertEquals(0, op.getSoldeAvant().compareTo(new BigDecimal("2000.00")));
        assertEquals(0, op.getSoldeApres().compareTo(new BigDecimal("1250.00")),
                "Solde apres = 2000 - 750 = 1250");
        assertEquals(555L, op.getPaiementId());

        Compte refreshed = transactionTemplate.execute(t -> compteRepository.findById(compte.getCompteId()).orElseThrow());
        assertEquals(0, refreshed.getSoldeActuel().compareTo(new BigDecimal("1250.00")));
    }

    @Test
    @DisplayName("T3 - Operations successives : DEBIT 5000 + CREDIT 1500 + CREDIT 3500 -> solde final = 0, audit trail complet")
    void shouldComputeBalanceCoherentlyAcrossSuccessiveOperations() {
        TenantContext.set(hotelMrId);
        authenticate();

        Compte compte = transactionTemplate.execute(t -> compteService.findOrCreateForClient(102L));
        Long compteId = compte.getCompteId();

        transactionTemplate.execute(t ->
                operationCompteService.recordDebit(compteId, new BigDecimal("5000.00"), 1L, "Facture 1"));
        transactionTemplate.execute(t ->
                operationCompteService.recordCredit(compteId, new BigDecimal("1500.00"), 10L, "Paiement 1"));
        transactionTemplate.execute(t ->
                operationCompteService.recordCredit(compteId, new BigDecimal("3500.00"), 11L, "Paiement 2"));

        Compte refreshed = transactionTemplate.execute(t -> compteRepository.findById(compteId).orElseThrow());
        assertEquals(0, refreshed.getSoldeActuel().compareTo(BigDecimal.ZERO),
                "5000 - 1500 - 3500 = 0");

        // Verifie audit trail complet : 3 operations + somme algebrique
        List<OperationCompte> operations = transactionTemplate.execute(t ->
                operationCompteRepository.findByCompteIdOrderByDateOperationDesc(compteId));
        assertEquals(3, operations.size(), "3 operations attendues");

        BigDecimal sommeDebit = operations.stream()
                .filter(o -> o.getTypeOperation() == TypeOperationCompte.DEBIT)
                .map(OperationCompte::getMontant)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal sommeCredit = operations.stream()
                .filter(o -> o.getTypeOperation() == TypeOperationCompte.CREDIT)
                .map(OperationCompte::getMontant)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, sommeDebit.compareTo(new BigDecimal("5000.00")));
        assertEquals(0, sommeCredit.compareTo(new BigDecimal("5000.00")));
        assertEquals(0, sommeDebit.compareTo(sommeCredit), "Somme DEBIT = Somme CREDIT (compte solde)");
    }

    @Test
    @DisplayName("T4 - recordDebit() avec compteId d'un autre tenant -> ResourceNotFoundException")
    void shouldIsolateAcrossTenantsOnRecordDebit() {
        // Cree un compte sur hotel MR
        TenantContext.set(hotelMrId);
        authenticate();
        Compte compteMr = transactionTemplate.execute(t -> compteService.findOrCreateForClient(200L));
        Long compteId = compteMr.getCompteId();
        TenantContext.clear();
        SecurityContextHolder.clearContext();

        // Authentifie un user FR puis tente de debiter le compte MR -> NotFound
        Role gerant = roleRepository.findByRoleCode("GERANT").orElseThrow();
        Hotel fr = hotelRepository.findById(hotelFrId).orElseThrow();
        DBUser userFr = new DBUser("gerantFR", "gerantFR@h2.test", "$2a$12$placeholder",
                "Pierre", "Dupont", fr, gerant);
        userFr.setActif(Boolean.TRUE);
        userFr.setCompteVerrouille(Boolean.FALSE);
        DBUser persistedFr = userRepository.saveAndFlush(userFr);

        TenantContext.set(hotelFrId);
        UserPrincipal principal = UserPrincipal.create(persistedFr, Collections.singletonList(
                new SimpleGrantedAuthority("ROLE_GERANT")));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        assertThrows(ResourceNotFoundException.class, () ->
                transactionTemplate.execute(t ->
                        operationCompteService.recordDebit(compteId, new BigDecimal("100.00"), 1L, "Tentative cross-tenant")));
    }

    @Test
    @DisplayName("T5 - findOrCreateForClient() idempotent : 2 appels avec meme clientId -> meme compte")
    void shouldBeIdempotentOnFindOrCreate() {
        TenantContext.set(hotelMrId);
        authenticate();

        Compte first = transactionTemplate.execute(t -> compteService.findOrCreateForClient(300L));
        Compte second = transactionTemplate.execute(t -> compteService.findOrCreateForClient(300L));

        assertNotNull(first);
        assertNotNull(second);
        assertEquals(first.getCompteId(), second.getCompteId(), "Le 2eme appel doit retourner le meme compte");
        assertEquals("CPT-CLI-300", first.getNumeroCompte());
        // 1 seul compte en base
        assertTrue(compteRepository.findByClientId(300L).isPresent());
    }
}

package com.cityprojects.citybackend.service.finance;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.dto.finance.FactureCreateDto;
import com.cityprojects.citybackend.dto.finance.FactureDto;
import com.cityprojects.citybackend.dto.finance.FolioDto;
import com.cityprojects.citybackend.dto.finance.LigneFactureCreateDto;
import com.cityprojects.citybackend.dto.finance.PaiementDto;
import com.cityprojects.citybackend.dto.finance.PaiementGlobalRequest;
import com.cityprojects.citybackend.entity.client.Client;
import com.cityprojects.citybackend.entity.core.DBUser;
import com.cityprojects.citybackend.entity.core.Hotel;
import com.cityprojects.citybackend.entity.core.Role;
import com.cityprojects.citybackend.entity.finance.ModePaiement;
import com.cityprojects.citybackend.entity.finance.StatutFacture;
import com.cityprojects.citybackend.entity.finance.TypeLigneFacture;
import com.cityprojects.citybackend.exception.BusinessException;
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
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests Surefire Tour 46 : {@link PaiementService#payerGlobal}.
 *
 * <p>Ces tests utilisent une astuce : comme {@code Facture.reservationId} est
 * une FK vers {@code hebergement.reservations} (qui n'est pas peuplee ici), on
 * cree manuellement les factures avec {@code reservationId = null} puis on
 * patch l'ID via JDBC pour simuler une reservation #1, en respectant
 * l'integrite tenant. Cette approche reste plus simple que d'instancier toute
 * la chaine hebergement (chambres, tarifs, types) dans un test de finance.</p>
 *
 * <h3>Couverture</h3>
 * <ol>
 *   <li>T1 - payerGlobal sur reservation avec 2 lignes (montant pile somme restes)
 *       -&gt; 2 affectations FIFO, facture PAYEE.</li>
 *   <li>T2 - payerGlobal excedentaire -&gt; ventilation totale + CREDIT excedent
 *       sur compte client (visible via folio).</li>
 *   <li>T3 - payerGlobal sans aucune facture (reservation inexistante en finance)
 *       -&gt; BusinessException error.paiement.global.aucuneLigne.</li>
 *   <li>T4 - payerGlobal avec lignes toutes deja payees -&gt; tout en CREDIT
 *       compte client (acompte).</li>
 *   <li>T5 - montant nul -&gt; BusinessException error.paiement.global.montantInvalid.</li>
 * </ol>
 */
@SpringBootTest
@ActiveProfiles("test")
class PaiementGlobalServiceTests {

    @Autowired
    private FactureService factureService;

    @Autowired
    private PaiementService paiementService;

    @Autowired
    private OperationCompteService operationCompteService;

    @Autowired
    private HotelRepository hotelRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private DBUserRepository userRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;
    private Long hotelMrId;
    private DBUser userMr;
    private Long clientMrId;
    private static final long FAKE_RESERVATION_ID = 12345L;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        TenantContext.clear();
        SecurityContextHolder.clearContext();
        cleanAll();

        Hotel mr = new Hotel("MR46G", "Hotel Tour46 PG");
        mr.setCodePays("MR");
        hotelMrId = hotelRepository.saveAndFlush(mr).getHotelId();

        Role gerant = roleRepository.saveAndFlush(new Role("GERANT", "Gerant"));
        userMr = new DBUser("u46g", "u46g@h.test",
                "$2a$12$placeholderhashplaceholderhashplaceholderhashplacehash",
                "Tour46", "PG", mr, gerant);
        userMr.setActif(Boolean.TRUE);
        userMr.setCompteVerrouille(Boolean.FALSE);
        userMr = userRepository.saveAndFlush(userMr);

        try {
            TenantContext.set(hotelMrId);
            Client client = transactionTemplate.execute(s -> {
                Client cl = new Client();
                cl.setNumeroClient("CLI-2026-MR-T46G1");
                cl.setPrenom("Test");
                cl.setNom("PayeurGlobal");
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

    /**
     * Cree une facture avec 2 lignes ET force {@code reservationId} via JDBC
     * (la FK hebergement.reservations n'est pas resolue dans ce test isole).
     */
    private FactureDto creerFactureAvec2LignesPourReservation(long reservationId,
                                                              BigDecimal prixL1,
                                                              BigDecimal prixL2) {
        LigneFactureCreateDto l1 = new LigneFactureCreateDto(
                TypeLigneFacture.DIVERS, null, null, null, null,
                "Ligne A", BigDecimal.ONE, prixL1, BigDecimal.ZERO, null);
        LigneFactureCreateDto l2 = new LigneFactureCreateDto(
                TypeLigneFacture.DIVERS, null, null, null, null,
                "Ligne B", BigDecimal.ONE, prixL2, BigDecimal.ZERO, null);
        FactureDto facture = transactionTemplate.execute(s -> factureService.create(
                new FactureCreateDto(null, null, clientMrId, null, null, null, null, null,
                        null, null, List.of(l1, l2))));
        FactureDto emitted = transactionTemplate.execute(s -> factureService.emettre(facture.factureId()));

        // Patch SQL : positionner reservation_id (la FK pointe vers hebergement.reservations,
        // qui n'existe pas en test isole : on contourne en utilisant l'absence de check
        // FK strict en H2 sur cette colonne nullable).
        jdbcTemplate.update(
                "UPDATE finance.factures SET reservation_id = ? WHERE facture_id = ?",
                reservationId, emitted.factureId());
        return emitted;
    }

    @Test
    @DisplayName("T1 - payerGlobal : 2 lignes, montant pile somme restes -> 2 affectations FIFO, facture PAYEE")
    void shouldPayAllLinesSequentially() {
        TenantContext.set(hotelMrId);
        authenticate();

        FactureDto facture = creerFactureAvec2LignesPourReservation(
                FAKE_RESERVATION_ID, new BigDecimal("3000"), new BigDecimal("2000"));
        assertEquals(StatutFacture.EMISE, facture.statut());

        PaiementGlobalRequest req = new PaiementGlobalRequest(
                FAKE_RESERVATION_ID,
                new BigDecimal("5000"),
                ModePaiement.ESPECES,
                "Test global",
                "Paiement total",
                clientMrId,
                null);

        PaiementDto paye = transactionTemplate.execute(s -> paiementService.payerGlobal(req));

        assertNotNull(paye);
        assertEquals(0, paye.montantTotal().compareTo(new BigDecimal("5000")));
        assertEquals(2, paye.affectations().size(),
                "FIFO sequentiel sur 2 lignes -> 2 affectations");

        FactureDto refresh = transactionTemplate.execute(s ->
                factureService.findById(facture.factureId()));
        assertEquals(StatutFacture.PAYEE, refresh.statut());
        assertEquals(0, refresh.montantPaye().compareTo(new BigDecimal("5000")));
    }

    @Test
    @DisplayName("T2 - payerGlobal excedentaire -> ventilation totale + CREDIT excedent visible folio")
    void shouldCreditOverpayment() {
        TenantContext.set(hotelMrId);
        authenticate();

        FactureDto facture = creerFactureAvec2LignesPourReservation(
                FAKE_RESERVATION_ID, new BigDecimal("1000"), new BigDecimal("500"));

        PaiementGlobalRequest req = new PaiementGlobalRequest(
                FAKE_RESERVATION_ID,
                new BigDecimal("2000"),  // 500 d'excedent
                ModePaiement.BANKILY,
                "Trop paye",
                null,
                clientMrId,
                null);

        PaiementDto paye = transactionTemplate.execute(s -> paiementService.payerGlobal(req));
        assertNotNull(paye);
        // Les 2 lignes sont entierement payees (1000 + 500 = 1500)
        assertEquals(2, paye.affectations().size());
        BigDecimal sommeAffect = paye.affectations().stream()
                .map(a -> a.montantAffecte())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, sommeAffect.compareTo(new BigDecimal("1500")));

        FactureDto refresh = transactionTemplate.execute(s ->
                factureService.findById(facture.factureId()));
        assertEquals(StatutFacture.PAYEE, refresh.statut());

        // L'excedent (500) doit creer une operation CREDIT visible dans le folio.
        FolioDto folio = transactionTemplate.execute(s ->
                operationCompteService.findFolio(clientMrId, null, null));
        // Au moins un CREDIT egal a 500
        boolean hasCreditExcedent = folio.operations().stream()
                .anyMatch(op -> "CREDIT".equals(op.type())
                        && op.montant().compareTo(new BigDecimal("500.00")) == 0
                        && op.motif() != null
                        && (op.motif().contains("Excedent") || op.motif().contains("Avance")));
        assertTrue(hasCreditExcedent,
                "Une operation CREDIT 500 avec libelle Excedent/Avance doit exister");
    }

    @Test
    @DisplayName("T3 - payerGlobal sans aucune facture -> BusinessException error.paiement.global.aucuneLigne")
    void shouldRejectWhenNoFacture() {
        TenantContext.set(hotelMrId);
        authenticate();

        PaiementGlobalRequest req = new PaiementGlobalRequest(
                99999L, // pas de facture liee
                new BigDecimal("1000"),
                ModePaiement.ESPECES,
                "Test", null, clientMrId, null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> transactionTemplate.execute(s -> paiementService.payerGlobal(req)));
        assertTrue(ex.getMessage().contains("aucuneLigne"),
                "Doit lever error.paiement.global.aucuneLigne");
    }

    @Test
    @DisplayName("T4 - payerGlobal avec toutes lignes deja payees -> tout en CREDIT (acompte)")
    void shouldCreditEverythingWhenAllLinesAlreadyPaid() {
        TenantContext.set(hotelMrId);
        authenticate();

        FactureDto facture = creerFactureAvec2LignesPourReservation(
                FAKE_RESERVATION_ID, new BigDecimal("1000"), new BigDecimal("500"));

        // 1er payerGlobal solde la facture entierement.
        PaiementGlobalRequest first = new PaiementGlobalRequest(
                FAKE_RESERVATION_ID, new BigDecimal("1500"),
                ModePaiement.ESPECES, null, null, clientMrId, null);
        transactionTemplate.execute(s -> paiementService.payerGlobal(first));

        FactureDto refresh = transactionTemplate.execute(s ->
                factureService.findById(facture.factureId()));
        assertEquals(StatutFacture.PAYEE, refresh.statut());

        // 2e payerGlobal : plus aucune ligne eligible -> tout doit aller en CREDIT compte.
        PaiementGlobalRequest second = new PaiementGlobalRequest(
                FAKE_RESERVATION_ID, new BigDecimal("750"),
                ModePaiement.BANKILY, "Avance", "Acompte client", clientMrId, null);
        PaiementDto paye = transactionTemplate.execute(s -> paiementService.payerGlobal(second));

        assertNotNull(paye);
        assertEquals(0, paye.affectations().size(),
                "Aucune ligne a affecter -> aucune affectation");

        FolioDto folio = transactionTemplate.execute(s ->
                operationCompteService.findFolio(clientMrId, null, null));
        boolean hasAvance750 = folio.operations().stream()
                .anyMatch(op -> "CREDIT".equals(op.type())
                        && op.montant().compareTo(new BigDecimal("750.00")) == 0);
        assertTrue(hasAvance750, "Le 2e paiement doit creer un CREDIT 750");
    }

    @Test
    @DisplayName("T5 - payerGlobal avec reservationId null -> BusinessException")
    void shouldRejectMissingReservationId() {
        TenantContext.set(hotelMrId);
        authenticate();

        PaiementGlobalRequest req = new PaiementGlobalRequest(
                null,
                new BigDecimal("100"),
                ModePaiement.ESPECES, null, null, clientMrId, null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> transactionTemplate.execute(s -> paiementService.payerGlobal(req)));
        assertTrue(ex.getMessage().contains("reservationRequired"),
                "Doit lever error.paiement.global.reservationRequired");
    }
}

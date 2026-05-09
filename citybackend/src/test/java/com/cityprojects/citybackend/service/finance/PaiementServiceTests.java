package com.cityprojects.citybackend.service.finance;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.dto.finance.FactureCreateDto;
import com.cityprojects.citybackend.dto.finance.FactureDto;
import com.cityprojects.citybackend.dto.finance.LigneFactureCreateDto;
import com.cityprojects.citybackend.dto.finance.PaiementCreateDto;
import com.cityprojects.citybackend.dto.finance.PaiementDto;
import com.cityprojects.citybackend.entity.core.DBUser;
import com.cityprojects.citybackend.entity.core.Hotel;
import com.cityprojects.citybackend.entity.core.Role;
import com.cityprojects.citybackend.entity.finance.ModePaiement;
import com.cityprojects.citybackend.entity.finance.StatutFacture;
import com.cityprojects.citybackend.entity.finance.StatutPaiement;
import com.cityprojects.citybackend.entity.finance.TypeLigneFacture;
import com.cityprojects.citybackend.exception.BusinessException;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests Surefire du {@link PaiementService}.
 *
 * <h3>Couverture</h3>
 * <ol>
 *   <li>T1 : create() simple avec affectation directe -&gt; numero PAY-{annee}-MR-000001
 *       et facture passe en PAYEE.</li>
 *   <li>T2 : paiement excessif (montant &gt; montantRestant facture) -&gt; BusinessException.</li>
 *   <li>T3 : paiement partiel -&gt; facture passe en PARTIELLEMENT_PAYEE.</li>
 * </ol>
 */
@SpringBootTest
@ActiveProfiles("test")
class PaiementServiceTests {

    @Autowired
    private FactureService factureService;

    @Autowired
    private PaiementService paiementService;

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
    private DBUser userMr;
    private int currentYear;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        TenantContext.clear();
        SecurityContextHolder.clearContext();
        currentYear = LocalDate.now().getYear();

        jdbcTemplate.update("DELETE FROM finance.affectations_paiements");
        jdbcTemplate.update("DELETE FROM finance.operations_comptes");
        jdbcTemplate.update("DELETE FROM finance.paiements");
        jdbcTemplate.update("DELETE FROM finance.lignes_factures");
        jdbcTemplate.update("DELETE FROM finance.factures");
        jdbcTemplate.update("DELETE FROM finance.comptes");
        jdbcTemplate.update("DELETE FROM finance.numerotation_sequence");
        jdbcTemplate.update("DELETE FROM core.dbusers");
        jdbcTemplate.update("DELETE FROM core.hotels");
        jdbcTemplate.update("DELETE FROM core.roles");

        Hotel mr = new Hotel("MR1", "Hotel Mauritanie");
        mr.setCodePays("MR");
        hotelMrId = hotelRepository.saveAndFlush(mr).getHotelId();

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
        jdbcTemplate.update("DELETE FROM finance.affectations_paiements");
        jdbcTemplate.update("DELETE FROM finance.operations_comptes");
        jdbcTemplate.update("DELETE FROM finance.paiements");
        jdbcTemplate.update("DELETE FROM finance.lignes_factures");
        jdbcTemplate.update("DELETE FROM finance.factures");
        jdbcTemplate.update("DELETE FROM finance.comptes");
        jdbcTemplate.update("DELETE FROM finance.numerotation_sequence");
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

    private FactureDto createEmittedFacture(BigDecimal montant) {
        LigneFactureCreateDto ligne = new LigneFactureCreateDto(
                TypeLigneFacture.DIVERS, null, null, null, null,
                "Test", BigDecimal.ONE, montant, BigDecimal.ZERO, null);
        FactureDto facture = transactionTemplate.execute(t -> factureService.create(
                new FactureCreateDto(null, null, null, null, null, null, null, null,
                        null, null, List.of(ligne))));
        return transactionTemplate.execute(t -> factureService.emettre(facture.factureId()));
    }

    @Test
    @DisplayName("T1 - paiement total avec factureId -> facture passe PAYEE, numero PAY-{annee}-MR-000001")
    void shouldCreatePaiementCompleteAndMarkFactureAsPayee() {
        TenantContext.set(hotelMrId);
        authenticate();

        FactureDto facture = createEmittedFacture(BigDecimal.valueOf(5000));
        assertEquals(StatutFacture.EMISE, facture.statut());

        PaiementCreateDto dto = new PaiementCreateDto(
                null, facture.factureId(), BigDecimal.valueOf(5000),
                "MRU", ModePaiement.ESPECES, null, null, null);

        PaiementDto created = transactionTemplate.execute(t -> paiementService.create(dto));

        assertNotNull(created);
        assertEquals(String.format("PAY-%d-MR-000001", currentYear), created.numeroPaiement());
        assertEquals(StatutPaiement.VALIDE, created.statut());
        assertEquals(0, created.montantTotal().compareTo(BigDecimal.valueOf(5000)));
        assertEquals(1, created.affectations().size());

        // La facture doit etre PAYEE maintenant
        FactureDto factureApres = transactionTemplate.execute(t -> factureService.findById(facture.factureId()));
        assertEquals(StatutFacture.PAYEE, factureApres.statut());
        assertEquals(0, factureApres.montantPaye().compareTo(BigDecimal.valueOf(5000)));
        assertEquals(0, factureApres.montantRestant().compareTo(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("T2 - paiement excessif (montant > montantRestant) -> BusinessException")
    void shouldRejectPaiementExcessif() {
        TenantContext.set(hotelMrId);
        authenticate();

        FactureDto facture = createEmittedFacture(BigDecimal.valueOf(1000));

        PaiementCreateDto dto = new PaiementCreateDto(
                null, facture.factureId(), BigDecimal.valueOf(2000),  // > montantTotal
                "MRU", ModePaiement.ESPECES, null, null, null);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                transactionTemplate.execute(t -> paiementService.create(dto)));
        assertTrue(ex.getMessage().contains("depasseMontantRestant")
                        || ex.getMessage().contains("error.paiement"),
                "Doit lever error.paiement.depasseMontantRestant");
    }

    @Test
    @DisplayName("T3 - paiement partiel -> facture passe PARTIELLEMENT_PAYEE")
    void shouldCreatePaiementPartielEtMarquerFacturePartiellementPayee() {
        TenantContext.set(hotelMrId);
        authenticate();

        FactureDto facture = createEmittedFacture(BigDecimal.valueOf(10000));

        PaiementCreateDto dto = new PaiementCreateDto(
                null, facture.factureId(), BigDecimal.valueOf(3000),
                "MRU", ModePaiement.BANKILY, "REF-BNK-001", null, null);

        PaiementDto created = transactionTemplate.execute(t -> paiementService.create(dto));
        assertEquals(0, created.montantTotal().compareTo(BigDecimal.valueOf(3000)));

        FactureDto factureApres = transactionTemplate.execute(t -> factureService.findById(facture.factureId()));
        assertEquals(StatutFacture.PARTIELLEMENT_PAYEE, factureApres.statut());
        assertEquals(0, factureApres.montantPaye().compareTo(BigDecimal.valueOf(3000)));
        assertEquals(0, factureApres.montantRestant().compareTo(BigDecimal.valueOf(7000)));
    }
}

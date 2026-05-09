package com.cityprojects.citybackend.service.restaurant;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.dto.finance.FactureCreateDto;
import com.cityprojects.citybackend.dto.finance.FactureDto;
import com.cityprojects.citybackend.dto.finance.LigneFactureCreateDto;
import com.cityprojects.citybackend.dto.finance.PaiementCreateDto;
import com.cityprojects.citybackend.dto.restaurant.ClotureCaisseDto;
import com.cityprojects.citybackend.entity.core.DBUser;
import com.cityprojects.citybackend.entity.core.Hotel;
import com.cityprojects.citybackend.entity.core.Role;
import com.cityprojects.citybackend.entity.finance.ModePaiement;
import com.cityprojects.citybackend.entity.finance.Paiement;
import com.cityprojects.citybackend.entity.finance.StatutPaiement;
import com.cityprojects.citybackend.entity.finance.TypeLigneFacture;
import com.cityprojects.citybackend.repository.core.DBUserRepository;
import com.cityprojects.citybackend.repository.core.HotelRepository;
import com.cityprojects.citybackend.repository.core.RoleRepository;
import com.cityprojects.citybackend.repository.finance.PaiementRepository;
import com.cityprojects.citybackend.security.UserPrincipal;
import com.cityprojects.citybackend.service.finance.FactureService;
import com.cityprojects.citybackend.service.finance.PaiementService;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests Surefire (rapides, en H2) du {@link CaisseService} (Tour 26.1).
 *
 * <h3>Couverture</h3>
 * <ol>
 *   <li>T1 : 3 paiements VALIDES (ESPECES 1500, BANKILY 2000, CARTE_BANCAIRE 800)
 *       + 1 ANNULE (mute manuellement) -&gt; statsJournalieres retourne les 3
 *       VALIDES, ventiles par mode + total 4300 + 3 transactions.</li>
 *   <li>T2 : isolation tenant - paiements crees dans hotel B ne remontent pas
 *       dans la cloture de hotel A (Hibernate @TenantId).</li>
 * </ol>
 */
@SpringBootTest
@ActiveProfiles("test")
class CaisseServiceTests {

    @Autowired private CaisseService caisseService;
    @Autowired private FactureService factureService;
    @Autowired private PaiementService paiementService;

    @Autowired private HotelRepository hotelRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private DBUserRepository userRepository;
    @Autowired private PaiementRepository paiementRepository;

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;

    private TransactionTemplate tx;
    private Long hotelMrId;
    private Long hotelFrId;
    private DBUser userMr;
    private DBUser userFr;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(transactionManager);
        TenantContext.clear();
        SecurityContextHolder.clearContext();
        cleanAll();

        Hotel mr = new Hotel("MR1", "Hotel Mauritanie");
        mr.setCodePays("MR");
        hotelMrId = hotelRepository.saveAndFlush(mr).getHotelId();

        Hotel fr = new Hotel("FR1", "Hotel France");
        fr.setCodePays("FR");
        hotelFrId = hotelRepository.saveAndFlush(fr).getHotelId();

        Role gerant = roleRepository.saveAndFlush(new Role("GERANT", "Gerant"));

        userMr = new DBUser("gerantmr", "gerant@mr.test",
                "$2a$12$placeholderhashplaceholderhashplaceholderhashplacehash",
                "Sidi", "Mohamed", mr, gerant);
        userMr.setActif(Boolean.TRUE);
        userMr.setCompteVerrouille(Boolean.FALSE);
        userMr = userRepository.saveAndFlush(userMr);

        userFr = new DBUser("gerantfr", "gerant@fr.test",
                "$2a$12$placeholderhashplaceholderhashplaceholderhashplacehash",
                "Jean", "Dupont", fr, gerant);
        userFr.setActif(Boolean.TRUE);
        userFr.setCompteVerrouille(Boolean.FALSE);
        userFr = userRepository.saveAndFlush(userFr);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
        cleanAll();
    }

    private void cleanAll() {
        jdbcTemplate.update("DELETE FROM finance.affectations_paiements");
        jdbcTemplate.update("DELETE FROM finance.operations_comptes");
        jdbcTemplate.update("DELETE FROM finance.paiements");
        jdbcTemplate.update("DELETE FROM finance.lignes_factures");
        jdbcTemplate.update("DELETE FROM finance.factures");
        jdbcTemplate.update("DELETE FROM finance.comptes");
        jdbcTemplate.update("DELETE FROM finance.numerotation_sequence");
        jdbcTemplate.update("DELETE FROM restaurant.tickets");
        jdbcTemplate.update("DELETE FROM restaurant.lignes_commande");
        jdbcTemplate.update("DELETE FROM restaurant.commandes");
        jdbcTemplate.update("DELETE FROM core.dbusers");
        jdbcTemplate.update("DELETE FROM core.hotels");
        jdbcTemplate.update("DELETE FROM core.roles");
    }

    private void authenticate(DBUser user) {
        UserPrincipal principal = UserPrincipal.create(user, Collections.singletonList(
                new SimpleGrantedAuthority("ROLE_GERANT")));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    /** Helper : cree une facture EMISE avec une ligne DIVERS du montant donne. */
    private FactureDto seedFactureEmise(BigDecimal montant) {
        LigneFactureCreateDto ligne = new LigneFactureCreateDto(
                TypeLigneFacture.DIVERS, null, null, null, null,
                "Test caisse", BigDecimal.ONE, montant, BigDecimal.ZERO, null);
        FactureDto facture = tx.execute(t -> factureService.create(
                new FactureCreateDto(null, null, null, null, null, null, null, null,
                        null, null, List.of(ligne))));
        return tx.execute(t -> factureService.emettre(facture.factureId()));
    }

    /** Helper : cree un paiement VALIDE pour une facture donnee. */
    private void seedPaiement(Long factureId, BigDecimal montant, ModePaiement mode) {
        tx.execute(t -> paiementService.create(new PaiementCreateDto(
                null, factureId, montant, "MRU", mode, null, null, "test")));
    }

    @Test
    @DisplayName("T1 - statsJournalieres : 3 paiements VALIDES + 1 ANNULE -> total 4300, 3 transactions")
    void shouldAggregateValidePaiementsByMode() {
        TenantContext.set(hotelMrId);
        authenticate(userMr);
        LocalDate today = LocalDate.now();

        // 3 paiements VALIDES sur factures distinctes
        FactureDto f1 = seedFactureEmise(BigDecimal.valueOf(1500));
        seedPaiement(f1.factureId(), BigDecimal.valueOf(1500), ModePaiement.ESPECES);

        FactureDto f2 = seedFactureEmise(BigDecimal.valueOf(2000));
        seedPaiement(f2.factureId(), BigDecimal.valueOf(2000), ModePaiement.BANKILY);

        FactureDto f3 = seedFactureEmise(BigDecimal.valueOf(800));
        seedPaiement(f3.factureId(), BigDecimal.valueOf(800), ModePaiement.CARTE_BANCAIRE);

        // 1 paiement supplementaire qu'on bascule en ANNULE manuellement (le service
        // n'expose pas de "create as ANNULE" : l'annulation passe par paiementService.annuler
        // mais elle exige un paiement non affecte). On force le statut via la DB
        // pour simuler un paiement historique annule. CECI EST LE MOYEN LE PLUS
        // SIMPLE pour tester le filtre statut=VALIDE.
        FactureDto f4 = seedFactureEmise(BigDecimal.valueOf(500));
        seedPaiement(f4.factureId(), BigDecimal.valueOf(500), ModePaiement.ESPECES);
        // Bascule le dernier paiement en ANNULE via SQL direct.
        jdbcTemplate.update(
                "UPDATE finance.paiements SET statut = 'ANNULE' "
                        + "WHERE paiement_id = (SELECT MAX(paiement_id) FROM finance.paiements)");

        // Verifie qu'on a bien 4 paiements en base, dont 1 ANNULE
        TenantContext.set(hotelMrId);
        long total = paiementRepository.count();
        assertEquals(4L, total);
        long annules = paiementRepository.findAll().stream()
                .filter(p -> p.getStatut() == StatutPaiement.ANNULE).count();
        assertEquals(1L, annules);

        // Appel cloture
        ClotureCaisseDto cloture = caisseService.statsJournalieres(today);

        assertNotNull(cloture);
        assertEquals(today, cloture.date());
        assertEquals(hotelMrId, cloture.hotelId());
        assertNotNull(cloture.generatedAt());

        // 3 modes presents (ESPECES, BANKILY, CARTE_BANCAIRE) - le 4eme en ANNULE est exclu
        assertEquals(3, cloture.totauxParMode().size(),
                "Doit y avoir exactement 3 modes (le ANNULE est exclu)");
        // ESPECES : 1500 (pas 2000 - le 500 ANNULE est exclu) sur 1 transaction
        ClotureCaisseDto.MontantNbPair especes = cloture.totauxParMode().get(ModePaiement.ESPECES);
        assertNotNull(especes);
        assertEquals(0, especes.montant().compareTo(new BigDecimal("1500")));
        assertEquals(1, especes.nombre());

        // BANKILY : 2000 / 1
        ClotureCaisseDto.MontantNbPair bankily = cloture.totauxParMode().get(ModePaiement.BANKILY);
        assertNotNull(bankily);
        assertEquals(0, bankily.montant().compareTo(new BigDecimal("2000")));
        assertEquals(1, bankily.nombre());

        // CARTE_BANCAIRE : 800 / 1
        ClotureCaisseDto.MontantNbPair cb = cloture.totauxParMode().get(ModePaiement.CARTE_BANCAIRE);
        assertNotNull(cb);
        assertEquals(0, cb.montant().compareTo(new BigDecimal("800")));
        assertEquals(1, cb.nombre());

        // Total global = 4300, 3 transactions VALIDES
        assertEquals(0, cloture.totalGlobal().compareTo(new BigDecimal("4300")));
        assertEquals(3, cloture.nbTransactionsTotal());

        // Pas de commandes encaissees ni annulees (on n'en a pas cree)
        assertEquals(0, cloture.nbCommandesEncaissees());
        assertEquals(0, cloture.nbCommandesAnnulees());
    }

    @Test
    @DisplayName("T2 - isolation tenant : paiements de hotel FR n'apparaissent pas dans la cloture de hotel MR")
    void shouldIsolatePaiementsByTenant() {
        LocalDate today = LocalDate.now();

        // Seed dans tenant FR : 1 paiement de 9999 MRU mode ESPECES
        TenantContext.set(hotelFrId);
        authenticate(userFr);
        FactureDto fFr = seedFactureEmise(BigDecimal.valueOf(9999));
        seedPaiement(fFr.factureId(), BigDecimal.valueOf(9999), ModePaiement.ESPECES);
        TenantContext.clear();
        SecurityContextHolder.clearContext();

        // Seed dans tenant MR : 1 paiement de 1500 mode BANKILY
        TenantContext.set(hotelMrId);
        authenticate(userMr);
        FactureDto fMr = seedFactureEmise(BigDecimal.valueOf(1500));
        seedPaiement(fMr.factureId(), BigDecimal.valueOf(1500), ModePaiement.BANKILY);

        // Cloture dans tenant MR : ne doit voir QUE le paiement MR
        ClotureCaisseDto cloture = caisseService.statsJournalieres(today);

        assertEquals(hotelMrId, cloture.hotelId());
        assertEquals(1, cloture.nbTransactionsTotal(),
                "Cloture MR ne doit voir QUE le paiement MR (Hibernate @TenantId)");
        assertEquals(0, cloture.totalGlobal().compareTo(new BigDecimal("1500")));
        assertTrue(cloture.totauxParMode().containsKey(ModePaiement.BANKILY));
        assertTrue(!cloture.totauxParMode().containsKey(ModePaiement.ESPECES),
                "Le paiement ESPECES de FR ne doit pas remonter dans la cloture MR");
    }
}

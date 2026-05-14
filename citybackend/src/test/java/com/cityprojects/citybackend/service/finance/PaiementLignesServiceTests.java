package com.cityprojects.citybackend.service.finance;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.dto.finance.FactureCreateDto;
import com.cityprojects.citybackend.dto.finance.FactureDto;
import com.cityprojects.citybackend.dto.finance.LigneFactureCreateDto;
import com.cityprojects.citybackend.dto.finance.PaiementDto;
import com.cityprojects.citybackend.dto.finance.PaiementLignesRequest;
import com.cityprojects.citybackend.entity.client.Client;
import com.cityprojects.citybackend.entity.core.DBUser;
import com.cityprojects.citybackend.entity.core.Hotel;
import com.cityprojects.citybackend.entity.core.Role;
import com.cityprojects.citybackend.entity.finance.ModePaiement;
import com.cityprojects.citybackend.entity.finance.StatutFacture;
import com.cityprojects.citybackend.entity.finance.TypeLigneFacture;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests Surefire Tour 45 : {@link PaiementService#paierLignes}.
 *
 * <h3>Couverture</h3>
 * <ol>
 *   <li>T1 : paiement partiel sur 2 lignes selectionnees (montant pile = somme
 *       restes) -&gt; 2 affectations creees, facture PARTIELLEMENT_PAYEE.</li>
 *   <li>T2 : paiement excedentaire -&gt; excedent credite sur le compte client
 *       (operation CREDIT visible via solde negatif sur DEBIT initial).</li>
 * </ol>
 */
@SpringBootTest
@ActiveProfiles("test")
class PaiementLignesServiceTests {

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
    private ClientRepository clientRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;
    private Long hotelMrId;
    private DBUser userMr;
    private Long clientMrId;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        TenantContext.clear();
        SecurityContextHolder.clearContext();
        cleanAll();

        Hotel mr = new Hotel("MR45F", "Hotel Tour 45 Finance");
        mr.setCodePays("MR");
        hotelMrId = hotelRepository.saveAndFlush(mr).getHotelId();

        Role gerant = roleRepository.saveAndFlush(new Role("GERANT", "Gerant"));
        userMr = new DBUser("u45f", "u45f@h.test",
                "$2a$12$placeholderhashplaceholderhashplaceholderhashplacehash",
                "Tour45", "Finance", mr, gerant);
        userMr.setActif(Boolean.TRUE);
        userMr.setCompteVerrouille(Boolean.FALSE);
        userMr = userRepository.saveAndFlush(userMr);

        try {
            TenantContext.set(hotelMrId);
            Client client = transactionTemplate.execute(s -> {
                Client cl = new Client();
                cl.setNumeroClient("CLI-2026-MR-T45F1");
                cl.setPrenom("Test");
                cl.setNom("PayeurT45");
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

    private FactureDto creerFactureAvec2Lignes() {
        LigneFactureCreateDto l1 = new LigneFactureCreateDto(
                TypeLigneFacture.DIVERS, null, null, null, null,
                "Ligne A", BigDecimal.ONE, new BigDecimal("3000"), BigDecimal.ZERO, null);
        LigneFactureCreateDto l2 = new LigneFactureCreateDto(
                TypeLigneFacture.DIVERS, null, null, null, null,
                "Ligne B", BigDecimal.ONE, new BigDecimal("2000"), BigDecimal.ZERO, null);
        FactureDto facture = transactionTemplate.execute(s -> factureService.create(
                new FactureCreateDto(null, null, clientMrId, null, null, null, null, null,
                        null, null, List.of(l1, l2))));
        return transactionTemplate.execute(s -> factureService.emettre(facture.factureId()));
    }

    @Test
    @DisplayName("T1 - paierLignes : 2 lignes selectionnees, montant pile somme restes -> 2 affectations + facture PAYEE")
    void shouldPayMultipleLignesProportionally() {
        TenantContext.set(hotelMrId);
        authenticate();

        FactureDto facture = creerFactureAvec2Lignes();
        assertEquals(StatutFacture.EMISE, facture.statut());
        assertEquals(2, facture.lignes().size());
        Long ligne1 = facture.lignes().get(0).ligneFactureId();
        Long ligne2 = facture.lignes().get(1).ligneFactureId();

        PaiementLignesRequest req = new PaiementLignesRequest(
                facture.factureId(),
                List.of(ligne1, ligne2),
                new BigDecimal("5000"), // total = restes
                ModePaiement.ESPECES,
                "Test paiement",
                "Encaissement complet",
                clientMrId,
                null);

        PaiementDto paye = transactionTemplate.execute(s -> paiementService.paierLignes(req));

        assertNotNull(paye);
        assertEquals(0, paye.montantTotal().compareTo(new BigDecimal("5000")));
        assertEquals(2, paye.affectations().size(), "Une affectation par ligne");
        assertTrue(paye.affectations().stream().anyMatch(a -> ligne1.equals(a.ligneFactureId())));
        assertTrue(paye.affectations().stream().anyMatch(a -> ligne2.equals(a.ligneFactureId())));

        // Facture doit etre PAYEE (montant pile)
        FactureDto refresh = transactionTemplate.execute(s ->
                factureService.findById(facture.factureId()));
        assertEquals(StatutFacture.PAYEE, refresh.statut());
    }

    @Test
    @DisplayName("T2 - paierLignes : paiement excedentaire -> excedent credite sur compte client")
    void shouldCreditOverpaymentOnClientAccount() {
        TenantContext.set(hotelMrId);
        authenticate();

        FactureDto facture = creerFactureAvec2Lignes();
        Long ligne1 = facture.lignes().get(0).ligneFactureId();

        // Restant de la ligne 1 = 3000, on paie 5000 -> 2000 d'excedent
        PaiementLignesRequest req = new PaiementLignesRequest(
                facture.factureId(),
                List.of(ligne1),
                new BigDecimal("5000"),
                ModePaiement.BANKILY,
                "Trop paye",
                null,
                clientMrId,
                null);

        PaiementDto paye = transactionTemplate.execute(s -> paiementService.paierLignes(req));

        assertNotNull(paye);
        // 1 affectation pour la ligne (3000), pas plus
        assertEquals(1, paye.affectations().size());
        assertEquals(0, paye.affectations().get(0).montantAffecte().compareTo(new BigDecimal("3000")));

        // L'excedent (2000) doit avoir cree une operation CREDIT.
        // Verification : la facture n'est pas (encore) PAYEE car ligne 2 n'a pas ete affectee
        FactureDto refresh = transactionTemplate.execute(s ->
                factureService.findById(facture.factureId()));
        assertEquals(StatutFacture.PARTIELLEMENT_PAYEE, refresh.statut());
        assertEquals(0, refresh.montantPaye().compareTo(new BigDecimal("3000")));
    }
}

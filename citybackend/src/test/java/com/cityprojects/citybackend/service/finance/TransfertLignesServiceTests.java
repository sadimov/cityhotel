package com.cityprojects.citybackend.service.finance;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.dto.finance.FactureCreateDto;
import com.cityprojects.citybackend.dto.finance.FactureDto;
import com.cityprojects.citybackend.dto.finance.LigneFactureCreateDto;
import com.cityprojects.citybackend.dto.finance.TransfertLignesRequest;
import com.cityprojects.citybackend.entity.client.Client;
import com.cityprojects.citybackend.entity.core.DBUser;
import com.cityprojects.citybackend.entity.core.Hotel;
import com.cityprojects.citybackend.entity.core.Role;
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

/**
 * Tests Surefire Tour 45 : {@link FactureService#transfererLignes}.
 *
 * <h3>Couverture</h3>
 * <ol>
 *   <li>T1 : transfert reussi - 1 ligne deplacee de facture A vers facture B,
 *       montants recalcules.</li>
 *   <li>T2 : refus si la facture source est ANNULEE -&gt; BusinessException.</li>
 * </ol>
 */
@SpringBootTest
@ActiveProfiles("test")
class TransfertLignesServiceTests {

    @Autowired
    private FactureService factureService;

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

        Hotel mr = new Hotel("MRTRL", "Hotel Transfert");
        mr.setCodePays("MR");
        hotelMrId = hotelRepository.saveAndFlush(mr).getHotelId();

        Role gerant = roleRepository.saveAndFlush(new Role("GERANT", "Gerant"));
        userMr = new DBUser("uTRL", "utrl@h.test",
                "$2a$12$placeholderhashplaceholderhashplaceholderhashplacehash",
                "Transfert", "User", mr, gerant);
        userMr.setActif(Boolean.TRUE);
        userMr.setCompteVerrouille(Boolean.FALSE);
        userMr = userRepository.saveAndFlush(userMr);

        try {
            TenantContext.set(hotelMrId);
            Client client = transactionTemplate.execute(s -> {
                Client cl = new Client();
                cl.setNumeroClient("CLI-2026-MR-TRL01");
                cl.setPrenom("Transfert");
                cl.setNom("Tester");
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

    private FactureDto creerFacture(BigDecimal montantLigne) {
        LigneFactureCreateDto ligne = new LigneFactureCreateDto(
                TypeLigneFacture.DIVERS, null, null, null, null,
                "Service", BigDecimal.ONE, montantLigne, BigDecimal.ZERO, null);
        FactureDto facture = transactionTemplate.execute(s -> factureService.create(
                new FactureCreateDto(null, null, clientMrId, null, null, null, null, null,
                        null, null, List.of(ligne))));
        return transactionTemplate.execute(s -> factureService.emettre(facture.factureId()));
    }

    @Test
    @DisplayName("T1 - transfererLignes : 1 ligne deplacee de A vers B, montants des 2 factures recalcules")
    void shouldTransferLineFromAtoB() {
        TenantContext.set(hotelMrId);
        authenticate();

        FactureDto factA = creerFacture(new BigDecimal("1000"));
        FactureDto factB = creerFacture(new BigDecimal("500"));
        Long ligneA = factA.lignes().get(0).ligneFactureId();

        TransfertLignesRequest req = new TransfertLignesRequest(
                List.of(ligneA), factB.factureId());

        FactureDto resultB = transactionTemplate.execute(s -> factureService.transfererLignes(req));

        assertNotNull(resultB);
        assertEquals(0, resultB.montantTtc().compareTo(new BigDecimal("1500")),
                "Facture B doit cumuler ses lignes : 500 (initiale) + 1000 (transferee) = 1500");
        assertEquals(2, resultB.lignes().size());

        FactureDto resultA = transactionTemplate.execute(s ->
                factureService.findById(factA.factureId()));
        assertEquals(0, resultA.montantTtc().compareTo(BigDecimal.ZERO),
                "Facture A doit avoir 0 ligne et donc montant 0");
    }

    @Test
    @DisplayName("T2 - transfererLignes refuse si facture source ANNULEE -> BusinessException")
    void shouldRejectIfSourceAnnulee() {
        TenantContext.set(hotelMrId);
        authenticate();

        FactureDto factA = creerFacture(new BigDecimal("1000"));
        FactureDto factB = creerFacture(new BigDecimal("500"));
        Long ligneA = factA.lignes().get(0).ligneFactureId();
        // Force ANNULEE en JDBC
        jdbcTemplate.update("UPDATE finance.factures SET statut = ? WHERE facture_id = ?",
                "ANNULEE", factA.factureId());

        TransfertLignesRequest req = new TransfertLignesRequest(
                List.of(ligneA), factB.factureId());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> transactionTemplate.execute(s -> factureService.transfererLignes(req)));
        assertEquals("error.facture.transfert.factureSourceTerminated", ex.getMessage());
    }
}

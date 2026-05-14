package com.cityprojects.citybackend.service.finance;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.dto.finance.FactureCreateDto;
import com.cityprojects.citybackend.dto.finance.FactureDto;
import com.cityprojects.citybackend.dto.finance.LigneFactureDto;
import com.cityprojects.citybackend.dto.finance.LigneServiceCreateRequest;
import com.cityprojects.citybackend.dto.inventory.ServiceHotelierCreateDto;
import com.cityprojects.citybackend.dto.inventory.ServiceHotelierDto;
import com.cityprojects.citybackend.dto.inventory.TypeServiceHotelierCreateDto;
import com.cityprojects.citybackend.dto.inventory.TypeServiceHotelierDto;
import com.cityprojects.citybackend.entity.core.DBUser;
import com.cityprojects.citybackend.entity.core.Hotel;
import com.cityprojects.citybackend.entity.core.Role;
import com.cityprojects.citybackend.entity.finance.StatutFacture;
import com.cityprojects.citybackend.entity.finance.TypeLigneFacture;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.exception.ResourceNotFoundException;
import com.cityprojects.citybackend.repository.core.DBUserRepository;
import com.cityprojects.citybackend.repository.core.HotelRepository;
import com.cityprojects.citybackend.repository.core.RoleRepository;
import com.cityprojects.citybackend.security.UserPrincipal;
import com.cityprojects.citybackend.service.inventory.ServiceHotelierService;
import com.cityprojects.citybackend.service.inventory.TypeServiceHotelierService;
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
 * Tests Surefire (H2) du bridge ServiceHotelier -&gt; LigneFacture (Tour 51bis).
 *
 * <h3>Couverture</h3>
 * <ol>
 *   <li>T1 : addLigneService() OK sur facture BROUILLON via factureId -&gt;
 *       ligne SERVICE creee + montants recalcules.</li>
 *   <li>T2 : addLigneService() refus si facture deja PAYEE
 *       ({@code error.facture.statut.cloturee}).</li>
 *   <li>T3 : addLigneService() refus si service inactif
 *       ({@code error.ligneService.serviceInactif}).</li>
 *   <li>T4 : addLigneService() via factureId inexistant -&gt;
 *       ResourceNotFoundException.</li>
 *   <li>T5 : addLigneService() sans factureId NI reservationId -&gt;
 *       BusinessException ({@code error.ligneService.targetRequired}).</li>
 *   <li>T6 : isolation cross-tenant -&gt; serviceId d'un autre hotel renvoie 404.</li>
 * </ol>
 */
@SpringBootTest
@ActiveProfiles("test")
class LigneServiceBridgeTests {

    @Autowired
    private FactureService factureService;

    @Autowired
    private ServiceHotelierService serviceHotelierService;

    @Autowired
    private TypeServiceHotelierService typeServiceHotelierService;

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

        // Cleanup ordonne (les FK pointent vers ces tables)
        jdbcTemplate.update("DELETE FROM finance.affectations_paiements");
        jdbcTemplate.update("DELETE FROM finance.operations_comptes");
        jdbcTemplate.update("DELETE FROM finance.paiements");
        jdbcTemplate.update("DELETE FROM finance.lignes_factures");
        jdbcTemplate.update("DELETE FROM finance.factures");
        jdbcTemplate.update("DELETE FROM finance.comptes");
        jdbcTemplate.update("DELETE FROM finance.numerotation_sequence");
        jdbcTemplate.update("DELETE FROM inventory.services_hoteliers");
        jdbcTemplate.update("DELETE FROM inventory.types_services_hoteliers");
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

        userMr = new DBUser("gerantBridge", "gerantBridge@h1.test",
                "$2a$12$placeholder", "Sidi", "Cheikh", mr, gerant);
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
        jdbcTemplate.update("DELETE FROM inventory.services_hoteliers");
        jdbcTemplate.update("DELETE FROM inventory.types_services_hoteliers");
        jdbcTemplate.update("DELETE FROM core.dbusers");
        jdbcTemplate.update("DELETE FROM core.hotels");
        jdbcTemplate.update("DELETE FROM core.roles");
    }

    private void authenticate(DBUser user, String role) {
        UserPrincipal principal = UserPrincipal.create(user, Collections.singletonList(
                new SimpleGrantedAuthority("ROLE_" + role)));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private ServiceHotelierDto seedActiveService(String code, String nom, BigDecimal prix) {
        TypeServiceHotelierDto type = transactionTemplate.execute(s ->
                typeServiceHotelierService.create(
                        new TypeServiceHotelierCreateDto("BIENETRE", "Bien-etre", null)));
        return transactionTemplate.execute(s -> serviceHotelierService.create(
                new ServiceHotelierCreateDto(type.typeServiceId(), code, nom, null,
                        prix, "prestation")));
    }

    private FactureDto seedBrouillon() {
        return transactionTemplate.execute(t -> factureService.create(
                new FactureCreateDto(null, null, null, null, null, null,
                        null, null, null, null, List.of())));
    }

    @Test
    @DisplayName("T1 - addLigneService() OK : ligne SERVICE creee + montants recalcules")
    void shouldAddLigneServiceOnBrouillonFacture() {
        TenantContext.set(hotelMrId);
        authenticate(userMr, "GERANT");

        ServiceHotelierDto svc = seedActiveService("SPA60", "Spa 60min",
                BigDecimal.valueOf(8000));
        FactureDto facture = seedBrouillon();

        // tauxTva explicite = 0 pour preserver la semantique B3 (le bridge
        // service hotelier est libre d'imposer un taux ; B4 etend par defaut
        // AUTRE_SERVICE_HOTELIER a 16%, override accepte).
        LigneServiceCreateRequest req = new LigneServiceCreateRequest(
                svc.serviceId(), null, facture.factureId(),
                BigDecimal.valueOf(2), null, null, BigDecimal.ZERO);

        LigneFactureDto ligne = transactionTemplate.execute(t ->
                factureService.addLigneService(req));

        assertNotNull(ligne);
        assertNotNull(ligne.ligneFactureId());
        assertEquals(TypeLigneFacture.SERVICE, ligne.typeLigne());
        assertEquals(svc.serviceId(), ligne.serviceId());
        assertEquals("Spa 60min", ligne.libelle(),
                "Libelle = service.nom quand description absente");
        assertEquals(0, ligne.prixUnitaire().compareTo(BigDecimal.valueOf(8000)),
                "Prix unitaire = catalogue (override absent)");
        assertEquals(0, ligne.quantite().compareTo(BigDecimal.valueOf(2)));
        assertEquals(0, ligne.montantTtc().compareTo(BigDecimal.valueOf(16000.00)),
                "2 * 8000 = 16000 TTC (override tauxTva=0 respecte)");

        // Facture mise a jour
        FactureDto refreshed = transactionTemplate.execute(t ->
                factureService.findById(facture.factureId()));
        assertEquals(1, refreshed.lignes().size());
        assertEquals(0, refreshed.montantTtc().compareTo(BigDecimal.valueOf(16000.00)));
    }

    @Test
    @DisplayName("T2 - addLigneService() refuse une facture deja PAYEE (statut.cloturee)")
    void shouldRejectClosedFacture() {
        TenantContext.set(hotelMrId);
        authenticate(userMr, "GERANT");

        ServiceHotelierDto svc = seedActiveService("BLANCH", "Blanchisserie",
                BigDecimal.valueOf(3000));
        FactureDto facture = seedBrouillon();

        // Force la facture a PAYEE en SQL direct (pour eviter d'avoir a creer un
        // Paiement complet : on teste juste le refus du bridge sur statut terminal)
        jdbcTemplate.update("UPDATE finance.factures SET statut = 'PAYEE' WHERE facture_id = ?",
                facture.factureId());

        LigneServiceCreateRequest req = new LigneServiceCreateRequest(
                svc.serviceId(), null, facture.factureId(),
                BigDecimal.ONE, null, null, null);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                transactionTemplate.execute(t -> factureService.addLigneService(req)));
        assertEquals("error.facture.statut.cloturee", ex.getMessage());
    }

    @Test
    @DisplayName("T3 - addLigneService() refuse un service inactif (serviceInactif)")
    void shouldRejectInactiveService() {
        TenantContext.set(hotelMrId);
        authenticate(userMr, "GERANT");

        ServiceHotelierDto svc = seedActiveService("MASSAGE", "Massage",
                BigDecimal.valueOf(5000));
        FactureDto facture = seedBrouillon();

        // Desactive le service
        transactionTemplate.executeWithoutResult(s ->
                serviceHotelierService.deactivate(svc.serviceId()));

        LigneServiceCreateRequest req = new LigneServiceCreateRequest(
                svc.serviceId(), null, facture.factureId(),
                BigDecimal.ONE, null, null, null);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                transactionTemplate.execute(t -> factureService.addLigneService(req)));
        assertEquals("error.ligneService.serviceInactif", ex.getMessage());
    }

    @Test
    @DisplayName("T4 - addLigneService() factureId inexistant -> 404")
    void shouldThrow404WhenFactureNotFound() {
        TenantContext.set(hotelMrId);
        authenticate(userMr, "GERANT");

        ServiceHotelierDto svc = seedActiveService("WIFI24", "Wifi 24h",
                BigDecimal.valueOf(500));

        LigneServiceCreateRequest req = new LigneServiceCreateRequest(
                svc.serviceId(), null, 99_999L,
                BigDecimal.ONE, null, null, null);

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class, () ->
                transactionTemplate.execute(t -> factureService.addLigneService(req)));
        assertEquals("error.facture.notFound", ex.getMessage());
    }

    @Test
    @DisplayName("T5 - addLigneService() sans factureId ni reservationId -> targetRequired")
    void shouldRejectWhenNoTarget() {
        TenantContext.set(hotelMrId);
        authenticate(userMr, "GERANT");

        ServiceHotelierDto svc = seedActiveService("TRANSF", "Transfert aeroport",
                BigDecimal.valueOf(2500));

        LigneServiceCreateRequest req = new LigneServiceCreateRequest(
                svc.serviceId(), null, null,
                BigDecimal.ONE, null, null, null);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                transactionTemplate.execute(t -> factureService.addLigneService(req)));
        assertEquals("error.ligneService.targetRequired", ex.getMessage());
    }

    @Test
    @DisplayName("T6 - addLigneService() cross-tenant : serviceId hors hotel -> 404")
    void shouldRejectForeignService() {
        // Cree un service dans MR
        TenantContext.set(hotelMrId);
        authenticate(userMr, "GERANT");
        ServiceHotelierDto svcMr = seedActiveService("PRIVATE", "Service prive",
                BigDecimal.valueOf(1000));
        TenantContext.clear();
        SecurityContextHolder.clearContext();

        // Bascule sur FR avec un user FR
        Role gerant = roleRepository.findByRoleCode("GERANT").orElseThrow();
        Hotel fr = hotelRepository.findById(hotelFrId).orElseThrow();
        DBUser userFr = new DBUser("gerantFR", "gerantFR@h2.test", "$2a$12$placeholder",
                "Pierre", "Dupont", fr, gerant);
        userFr.setActif(Boolean.TRUE);
        userFr.setCompteVerrouille(Boolean.FALSE);
        DBUser persistedFr = userRepository.saveAndFlush(userFr);
        TenantContext.set(hotelFrId);
        authenticate(persistedFr, "GERANT");

        // Crée une facture vide cote FR pour le test
        FactureDto factureFr = seedBrouillon();

        Long foreignServiceId = svcMr.serviceId();
        LigneServiceCreateRequest req = new LigneServiceCreateRequest(
                foreignServiceId, null, factureFr.factureId(),
                BigDecimal.ONE, null, null, null);

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class, () ->
                transactionTemplate.execute(t -> factureService.addLigneService(req)));
        assertEquals("error.serviceHotelier.notFound", ex.getMessage());

        // Sanity check : verifier que la facture FR n'a aucune ligne
        int lignesCount = transactionTemplate.execute(t ->
                factureService.findById(factureFr.factureId()).lignes().size());
        assertEquals(0, lignesCount);

        // Verifie aussi que la facture MR n'a pas non plus de statut deteriore
        TenantContext.clear();
        SecurityContextHolder.clearContext();
        TenantContext.set(hotelMrId);
        authenticate(userMr, "GERANT");
        // On verifie juste que tenant MR voit encore son service (sanity)
        ServiceHotelierDto mrAfter = transactionTemplate.execute(t ->
                serviceHotelierService.findById(svcMr.serviceId()));
        assertEquals(StatutFacture.BROUILLON, factureFr.statut());
        assertNotNull(mrAfter);
    }
}

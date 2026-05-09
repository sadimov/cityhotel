package com.cityprojects.citybackend.service.client;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.dto.client.ClientCreateDto;
import com.cityprojects.citybackend.dto.client.ClientDto;
import com.cityprojects.citybackend.entity.core.Hotel;
import com.cityprojects.citybackend.exception.ResourceNotFoundException;
import com.cityprojects.citybackend.repository.core.HotelRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests Surefire (rapides, en H2) du {@link ClientService}.
 * <p>
 * Couverture :
 * <ol>
 *   <li>T1 : create() genere un numeroClient {@code CLI-{annee}-{codePays}-000001}
 *       et persiste l'entite dans le tenant courant.</li>
 *   <li>T2 : findById() ne retourne pas une entite d'un autre tenant
 *       (cross-tenant -&gt; ResourceNotFoundException).</li>
 *   <li>T3 : findAllActive(Pageable) ne retourne que les clients du tenant courant.</li>
 *   <li>T4 : deactivate() met actif=false et le client n'apparait plus dans findAllActive.</li>
 *   <li>T5 : sans TenantContext -&gt; @RequireTenant rejette avec error.tenant.missing.</li>
 * </ol>
 *
 * <h3>Strategies importantes</h3>
 * <ul>
 *   <li>Pas de @Transactional sur les methodes de test : on utilise
 *       {@link TransactionTemplate} pour controler precisement quand le tenant
 *       est resolu (cf. pattern de {@code TenantMultiTenancyIT} et
 *       {@code NumerotationServiceTests}).</li>
 *   <li>Cleanup brut SQL via {@link JdbcTemplate} pour eviter les interactions
 *       avec le filtre tenant (independant du resolver).</li>
 *   <li>Seed de 2 hotels (MR + FR) pour garantir l'isolation et le bon
 *       formatage du codePays dans le numeroClient.</li>
 * </ul>
 *
 * <p><b>Note de classification</b> : ce test demarre Spring + JPA + H2, donc
 * "techniquement integration", mais reste classe en {@code *Tests} (Surefire)
 * conformement aux autres tests de service du projet (cf. NumerotationServiceTests
 * §convention CLAUDE.md §7).</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class ClientServiceTests {

    @Autowired
    private ClientService clientService;

    @Autowired
    private HotelRepository hotelRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;
    private Long hotelMrId;
    private Long hotelFrId;
    private int currentYear;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        TenantContext.clear();
        currentYear = java.time.LocalDate.now().getYear();

        // Cleanup avant seed (contexte Spring partage entre tests)
        jdbcTemplate.update("DELETE FROM client.clients");
        jdbcTemplate.update("DELETE FROM client.societes");
        jdbcTemplate.update("DELETE FROM finance.numerotation_sequence");
        jdbcTemplate.update("DELETE FROM core.hotels");

        Hotel mr = new Hotel("MRH001", "Hotel Mauritanie");
        mr.setCodePays("MR");
        hotelMrId = hotelRepository.saveAndFlush(mr).getHotelId();

        Hotel fr = new Hotel("FRH001", "Hotel France");
        fr.setCodePays("FR");
        hotelFrId = hotelRepository.saveAndFlush(fr).getHotelId();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        jdbcTemplate.update("DELETE FROM client.clients");
        jdbcTemplate.update("DELETE FROM client.societes");
        jdbcTemplate.update("DELETE FROM finance.numerotation_sequence");
        jdbcTemplate.update("DELETE FROM core.hotels");
    }

    @Test
    @DisplayName("T1 - create() genere numeroClient CLI-{annee}-MR-000001 et hotelId est resolu via TenantContext")
    void shouldCreateClientWithGeneratedNumero() {
        TenantContext.set(hotelMrId);

        ClientCreateDto dto = new ClientCreateDto(
                "Mohamed", "Salem", null, "+22244111111",
                "mohamed.salem@example.mr", null, "Nouakchott", "Mauritanie",
                null, null, null, null);

        ClientDto created = transactionTemplate.execute(s -> clientService.create(dto));

        assertNotNull(created);
        assertNotNull(created.clientId(), "id genere par la base");
        assertEquals(String.format("CLI-%d-MR-000001", currentYear), created.numeroClient(),
                "Le numero doit suivre le format CLI-{annee}-{codePays}-{6 chiffres}");
        assertEquals("Mohamed", created.prenom());
        assertEquals("Salem", created.nom());
        assertEquals("Mohamed Salem", created.nomComplet());
        assertTrue(Boolean.TRUE.equals(created.actif()));
    }

    @Test
    @DisplayName("T2 - findById() depuis un autre tenant -> ResourceNotFoundException (isolation Hibernate)")
    void shouldNotFindCrossTenantClient() {
        // Cree un client dans hotel MR
        TenantContext.set(hotelMrId);
        ClientCreateDto dto = new ClientCreateDto(
                "Awa", "Diallo", null, null, null, null, null, null,
                null, null, null, null);
        ClientDto created = transactionTemplate.execute(s -> clientService.create(dto));
        TenantContext.clear();

        // Tente de le lire depuis hotel FR -> Hibernate filtre, repository retourne empty
        TenantContext.set(hotelFrId);
        Long foreignId = created.clientId();
        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> transactionTemplate.execute(s -> clientService.findById(foreignId)));
        assertEquals("error.client.notFound", ex.getMessage());
    }

    @Test
    @DisplayName("T3 - findAllActive() retourne uniquement les clients du tenant courant")
    void shouldFilterAllActiveByTenant() {
        // 2 clients dans hotel MR
        TenantContext.set(hotelMrId);
        transactionTemplate.execute(s -> clientService.create(new ClientCreateDto(
                "Ahmed", "Ould Ali", null, null, null, null, null, null,
                null, null, null, null)));
        transactionTemplate.execute(s -> clientService.create(new ClientCreateDto(
                "Fatima", "Mint Mohamed", null, null, null, null, null, null,
                null, null, null, null)));
        TenantContext.clear();

        // 1 client dans hotel FR
        TenantContext.set(hotelFrId);
        transactionTemplate.execute(s -> clientService.create(new ClientCreateDto(
                "Pierre", "Dupont", null, null, null, null, null, null,
                null, null, null, null)));
        TenantContext.clear();

        // Vue depuis hotel MR -> 2
        TenantContext.set(hotelMrId);
        Page<ClientDto> mrPage = transactionTemplate.execute(s ->
                clientService.findAllActive(PageRequest.of(0, 50)));
        assertNotNull(mrPage);
        assertEquals(2L, mrPage.getTotalElements(),
                "Hotel MR doit voir uniquement ses 2 clients");
        TenantContext.clear();

        // Vue depuis hotel FR -> 1
        TenantContext.set(hotelFrId);
        Page<ClientDto> frPage = transactionTemplate.execute(s ->
                clientService.findAllActive(PageRequest.of(0, 50)));
        assertNotNull(frPage);
        assertEquals(1L, frPage.getTotalElements(),
                "Hotel FR doit voir uniquement son client");
    }

    @Test
    @DisplayName("T4 - deactivate() retire le client de findAllActive() ; findById le retrouve toujours")
    void shouldDeactivateClient() {
        TenantContext.set(hotelMrId);

        ClientDto created = transactionTemplate.execute(s -> clientService.create(new ClientCreateDto(
                "Sidi", "Cheikh", null, null, null, null, null, null,
                null, null, null, null)));

        Long createdId = created.clientId();
        transactionTemplate.executeWithoutResult(s -> clientService.deactivate(createdId));

        // findById renvoie toujours l'entite (filtre actif n'est applique que sur findAllActive)
        ClientDto reloaded = transactionTemplate.execute(s -> clientService.findById(createdId));
        assertEquals(Boolean.FALSE, reloaded.actif());

        // findAllActive ne le voit plus
        Page<ClientDto> activePage = transactionTemplate.execute(s ->
                clientService.findAllActive(PageRequest.of(0, 50)));
        assertEquals(0L, activePage.getTotalElements(),
                "Un client desactive ne doit plus apparaitre dans findAllActive");
    }

    @Test
    @DisplayName("T5 - sans TenantContext -> @RequireTenant rejette avec error.tenant.missing")
    void shouldRejectWhenTenantAbsent() {
        ClientCreateDto dto = new ClientCreateDto(
                "Anonyme", "Sans Tenant", null, null, null, null, null, null,
                null, null, null, null);
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> transactionTemplate.execute(s -> clientService.create(dto)));
        assertEquals("error.tenant.missing", ex.getMessage());
    }
}

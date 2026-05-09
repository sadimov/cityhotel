package com.cityprojects.citybackend.service.restaurant;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.dto.restaurant.ArticleMenuCreateDto;
import com.cityprojects.citybackend.dto.restaurant.ArticleMenuDto;
import com.cityprojects.citybackend.dto.restaurant.CategorieMenuCreateDto;
import com.cityprojects.citybackend.dto.restaurant.CategorieMenuDto;
import com.cityprojects.citybackend.dto.restaurant.CommandeCreateDto;
import com.cityprojects.citybackend.dto.restaurant.CommandeDto;
import com.cityprojects.citybackend.dto.restaurant.LigneCommandeCreateDto;
import com.cityprojects.citybackend.dto.restaurant.TicketDto;
import com.cityprojects.citybackend.entity.core.DBUser;
import com.cityprojects.citybackend.entity.core.Hotel;
import com.cityprojects.citybackend.entity.core.Role;
import com.cityprojects.citybackend.entity.restaurant.ModeReglementCommande;
import com.cityprojects.citybackend.entity.restaurant.TypeTicket;
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
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests Surefire (rapides, en H2) du {@link TicketService} (Tour 24).
 *
 * <h3>Couverture</h3>
 * <ol>
 *   <li>T1 : imprimerTicketCaisse() cree un Ticket(typeTicket=CAISSE) avec
 *       userId du SecurityContext.</li>
 *   <li>T2 : reimprimer() sans motif -&gt; BusinessException
 *       (error.ticket.motif.required).</li>
 * </ol>
 */
@SpringBootTest
@ActiveProfiles("test")
class TicketServiceTests {

    @Autowired private TicketService ticketService;
    @Autowired private CommandeService commandeService;
    @Autowired private CategorieMenuService categorieService;
    @Autowired private ArticleMenuService articleService;

    @Autowired private HotelRepository hotelRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private DBUserRepository userRepository;

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;

    private TransactionTemplate tx;
    private Long hotelMrId;
    private DBUser userGerant;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(transactionManager);
        TenantContext.clear();
        cleanAll();

        Hotel mr = new Hotel("MR1", "Hotel Mauritanie");
        mr.setCodePays("MR");
        hotelMrId = hotelRepository.saveAndFlush(mr).getHotelId();

        Role gerant = roleRepository.saveAndFlush(new Role("GERANT", "Gerant"));
        userGerant = userRepository.saveAndFlush(new DBUser(
                "gerant1", "gerant1@mr.test",
                "$2a$12$placeholderhashplaceholderhashplaceholderhashplacehash",
                "Sidi", "Mohamed", mr, gerant));

        UserPrincipal principal = UserPrincipal.create(userGerant,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_GERANT")));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
        cleanAll();
    }

    private void cleanAll() {
        jdbcTemplate.update("DELETE FROM finance.numerotation_sequence");
        jdbcTemplate.update("DELETE FROM restaurant.tickets");
        jdbcTemplate.update("DELETE FROM restaurant.lignes_commande");
        jdbcTemplate.update("DELETE FROM restaurant.commandes");
        jdbcTemplate.update("DELETE FROM restaurant.articles_menus");
        jdbcTemplate.update("DELETE FROM restaurant.categories_menus");
        jdbcTemplate.update("DELETE FROM core.dbusers");
        jdbcTemplate.update("DELETE FROM core.hotels");
        jdbcTemplate.update("DELETE FROM core.roles");
    }

    private Long seedCommande() {
        CategorieMenuDto cat = tx.execute(s -> categorieService.create(
                new CategorieMenuCreateDto("Plats", null, null, 0)));
        ArticleMenuDto art = tx.execute(s -> articleService.create(new ArticleMenuCreateDto(
                "PLT1", "Riz au poisson", null,
                cat.categorieId(), BigDecimal.valueOf(1500), null, Boolean.TRUE)));

        CommandeDto created = tx.execute(s -> commandeService.create(new CommandeCreateDto(
                ModeReglementCommande.COMPTANT, null, null, "MRU", null,
                List.of(new LigneCommandeCreateDto(art.articleId(),
                        BigDecimal.ONE, null, null)))));
        return created.commandeId();
    }

    @Test
    @DisplayName("T1 - imprimerTicketCaisse() cree un Ticket CAISSE avec userId du SecurityContext")
    void shouldImprimerTicketCaisse() {
        TenantContext.set(hotelMrId);
        Long commandeId = seedCommande();

        TicketDto ticket = tx.execute(s -> ticketService.imprimerTicketCaisse(commandeId));

        assertNotNull(ticket);
        assertNotNull(ticket.ticketId());
        assertEquals(commandeId, ticket.commandeId());
        assertEquals(TypeTicket.CAISSE, ticket.typeTicket());
        assertNotNull(ticket.dateImpression());
        assertEquals(userGerant.getUserId(), ticket.imprimeParUserId());
        assertNull(ticket.motifReimpression());

        // Verification hotel_id en base
        Long hotelIdInDb = jdbcTemplate.queryForObject(
                "SELECT hotel_id FROM restaurant.tickets WHERE ticket_id = ?",
                Long.class, ticket.ticketId());
        assertEquals(hotelMrId, hotelIdInDb);
    }

    @Test
    @DisplayName("T2 - reimprimer() sans motif -> BusinessException error.ticket.motif.required")
    void shouldRejectReimpressionWithoutMotif() {
        TenantContext.set(hotelMrId);
        Long commandeId = seedCommande();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> tx.execute(s -> ticketService.reimprimer(commandeId, TypeTicket.CAISSE, null)));
        assertEquals("error.ticket.motif.required", ex.getMessage());

        BusinessException ex2 = assertThrows(BusinessException.class,
                () -> tx.execute(s -> ticketService.reimprimer(commandeId, TypeTicket.CAISSE, "")));
        assertEquals("error.ticket.motif.required", ex2.getMessage());
    }

    @Test
    @DisplayName("T3 - reimprimer() avec motif valide cree un Ticket REIMPRESSION trace")
    void shouldReimprimerAvecMotif() {
        TenantContext.set(hotelMrId);
        Long commandeId = seedCommande();

        // 1ere impression caisse
        tx.execute(s -> ticketService.imprimerTicketCaisse(commandeId));

        // Reimpression
        TicketDto reimp = tx.execute(s -> ticketService.reimprimer(
                commandeId, TypeTicket.CAISSE, "Client a perdu le ticket"));

        assertNotNull(reimp);
        assertEquals(TypeTicket.REIMPRESSION, reimp.typeTicket());
        assertEquals("Client a perdu le ticket", reimp.motifReimpression());

        // 2 tickets en base
        List<TicketDto> tickets = ticketService.listerParCommande(commandeId);
        assertEquals(2, tickets.size());
    }
}

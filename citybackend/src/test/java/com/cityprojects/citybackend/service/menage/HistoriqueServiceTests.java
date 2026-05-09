package com.cityprojects.citybackend.service.menage;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.dto.menage.HistoriqueDto;
import com.cityprojects.citybackend.dto.menage.TacheCreateDto;
import com.cityprojects.citybackend.dto.menage.TacheDto;
import com.cityprojects.citybackend.entity.core.Hotel;
import com.cityprojects.citybackend.entity.hebergement.Chambre;
import com.cityprojects.citybackend.entity.hebergement.StatutChambre;
import com.cityprojects.citybackend.entity.hebergement.TypeChambre;
import com.cityprojects.citybackend.entity.menage.TypeNettoyage;
import com.cityprojects.citybackend.repository.core.HotelRepository;
import com.cityprojects.citybackend.repository.hebergement.ChambreRepository;
import com.cityprojects.citybackend.repository.hebergement.TypeChambreRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests Surefire (H2) du {@link HistoriqueService} (Tour 30 etape 7).
 *
 * <p>Couvre la garde de retention minimale ajoutee au Tour 30 : tout appel a
 * {@code nettoyer()} avec une retention &lt; {@code MIN_RETENTION_DAYS} (30 jours)
 * doit lever une {@link IllegalArgumentException} avec la cle i18n
 * {@code "error.historique.retention.min"} (mappee en HTTP 400 par
 * {@code GlobalExceptionHandler}).</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class HistoriqueServiceTests {

    @Autowired
    private HistoriqueService historiqueService;

    @Autowired
    private TacheService tacheService;

    @Autowired
    private HotelRepository hotelRepository;

    @Autowired
    private TypeChambreRepository typeChambreRepository;

    @Autowired
    private ChambreRepository chambreRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate tx;
    private Long hotelMrId;
    private Long hotelFrId;
    private Long chambreMrId;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(transactionManager);
        TenantContext.clear();

        jdbcTemplate.update("DELETE FROM menage.historique");
        jdbcTemplate.update("DELETE FROM menage.taches");
        jdbcTemplate.update("DELETE FROM menage.planning");
        jdbcTemplate.update("DELETE FROM menage.personnel");
        jdbcTemplate.update("DELETE FROM hebergement.reservations_chambres");
        jdbcTemplate.update("DELETE FROM hebergement.reservations_clients");
        jdbcTemplate.update("DELETE FROM hebergement.nuitees");
        jdbcTemplate.update("DELETE FROM hebergement.reservations");
        jdbcTemplate.update("DELETE FROM hebergement.chambres");
        jdbcTemplate.update("DELETE FROM hebergement.tarifs_chambres");
        jdbcTemplate.update("DELETE FROM hebergement.types_chambres");
        jdbcTemplate.update("DELETE FROM core.dbusers");
        jdbcTemplate.update("DELETE FROM core.hotels");
        jdbcTemplate.update("DELETE FROM core.roles");

        Hotel mr = new Hotel("MR1", "Hotel Mauritanie");
        mr.setCodePays("MR");
        hotelMrId = hotelRepository.saveAndFlush(mr).getHotelId();

        Hotel fr = new Hotel("FR1", "Hotel France");
        fr.setCodePays("FR");
        hotelFrId = hotelRepository.saveAndFlush(fr).getHotelId();

        // Seed Tour 31 : une chambre cote MR pour pouvoir creer des taches MR
        // dont l'historique sera ensuite filtre par tenant.
        try {
            TenantContext.set(hotelMrId);
            TypeChambre type = tx.execute(s -> {
                TypeChambre t = new TypeChambre();
                t.setTypeCode("STD");
                t.setTypeNom("Standard");
                t.setNbLitsMax(2);
                t.setNbPersonnesMax(2);
                t.setActif(Boolean.TRUE);
                return typeChambreRepository.save(t);
            });
            Chambre chambre = tx.execute(s -> {
                Chambre c = new Chambre();
                c.setNumeroChambre("401");
                c.setTypeId(type.getTypeId());
                c.setStatut(StatutChambre.DISPONIBLE);
                c.setNbLits(1);
                c.setNbPersonnesMax(2);
                c.setActif(Boolean.TRUE);
                return chambreRepository.save(c);
            });
            chambreMrId = chambre.getChambreId();
        } finally {
            TenantContext.clear();
        }
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        jdbcTemplate.update("DELETE FROM menage.historique");
        jdbcTemplate.update("DELETE FROM menage.taches");
        jdbcTemplate.update("DELETE FROM menage.planning");
        jdbcTemplate.update("DELETE FROM menage.personnel");
        jdbcTemplate.update("DELETE FROM hebergement.reservations_chambres");
        jdbcTemplate.update("DELETE FROM hebergement.reservations_clients");
        jdbcTemplate.update("DELETE FROM hebergement.nuitees");
        jdbcTemplate.update("DELETE FROM hebergement.reservations");
        jdbcTemplate.update("DELETE FROM hebergement.chambres");
        jdbcTemplate.update("DELETE FROM hebergement.tarifs_chambres");
        jdbcTemplate.update("DELETE FROM hebergement.types_chambres");
        jdbcTemplate.update("DELETE FROM core.dbusers");
        jdbcTemplate.update("DELETE FROM core.hotels");
        jdbcTemplate.update("DELETE FROM core.roles");
    }

    /**
     * Tour 30 etape 7 : refus si la retention demandee est inferieure au plancher
     * MIN_RETENTION_DAYS (30 jours).
     */
    @Test
    @DisplayName("H1 - Etape 7 : nettoyer(7) -> IllegalArgumentException 'error.historique.retention.min'")
    void shouldRejectNettoyerBelowMinRetention() {
        TenantContext.set(hotelMrId);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tx.execute(s -> historiqueService.nettoyer(7)));
        assertEquals("error.historique.retention.min", ex.getMessage());
    }

    /**
     * Tour 30 etape 7 : nettoyer(0) refuse aussi (couvre le cas limite "purge tout").
     */
    @Test
    @DisplayName("H2 - Etape 7 : nettoyer(0) -> IllegalArgumentException 'error.historique.retention.min'")
    void shouldRejectNettoyerWithZeroRetention() {
        TenantContext.set(hotelMrId);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tx.execute(s -> historiqueService.nettoyer(0)));
        assertEquals("error.historique.retention.min", ex.getMessage());
    }

    /**
     * Tour 30 etape 7 : nettoyer(30) doit passer (cas frontiere = MIN_RETENTION_DAYS).
     */
    @Test
    @DisplayName("H3 - Etape 7 : nettoyer(30) accepte (cas frontiere = MIN_RETENTION_DAYS)")
    void shouldAcceptNettoyerAtMinRetention() {
        TenantContext.set(hotelMrId);
        // Aucun historique en base : la purge retourne 0 mais ne doit pas lever.
        Integer n = tx.execute(s -> historiqueService.nettoyer(30));
        assertEquals(0, n);
    }

    /**
     * Tour 31 (audit Tour 29 multitenant-guardian) : assertion explicite
     * d'isolation Hibernate sur l'historique par {@code tacheId}.
     *
     * <p>Setup : on cree une tache cote MR. Le hook {@code @AuditAction}
     * insere automatiquement une entree {@code historique} (action=creation).
     * Verification : depuis le tenant FR, {@code findByTache(tacheId_MR)}
     * doit retourner une liste vide (pas d'exception, juste 0 resultat) car
     * Hibernate filtre {@code WHERE hotel_id = ?} via {@code @TenantId} sur
     * {@code Historique}.</p>
     */
    @Test
    @DisplayName("H4 - Tour 31 : findByTache() depuis un autre tenant -> liste vide (Hibernate @TenantId)")
    void shouldNotFindCrossTenantHistoriqueByTache() {
        // Cree la tache MR -> declenche l'insertion d'un Historique MR
        TenantContext.set(hotelMrId);
        TacheDto tacheMr = tx.execute(s -> tacheService.create(
                new TacheCreateDto(chambreMrId, null, TypeNettoyage.QUOTIDIEN, 1,
                        LocalDate.now(), null, null, "tache MR", null)));
        assertNotNull(tacheMr.tacheId());

        // Sanity : sous TenantContext = MR, on a bien au moins 1 entree historique
        List<HistoriqueDto> histMr = tx.execute(s -> historiqueService.findByTache(tacheMr.tacheId()));
        assertTrue(histMr.size() >= 1,
                "Sanity : la creation de tache cote MR doit avoir laisse au moins une entree d'historique");

        // Switch tenant : sous FR, la meme requete doit retourner 0 (Hibernate filtre)
        TenantContext.clear();
        TenantContext.set(hotelFrId);
        Long foreignTacheId = tacheMr.tacheId();
        List<HistoriqueDto> histFr = tx.execute(s -> historiqueService.findByTache(foreignTacheId));
        assertNotNull(histFr);
        assertEquals(0, histFr.size(),
                "Hibernate @TenantId doit filtrer : aucun historique d'une tache MR ne doit "
                        + "remonter sous TenantContext = FR");
    }
}

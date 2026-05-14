package com.cityprojects.citybackend.service.hebergement;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.dto.hebergement.MontantCalculDto;
import com.cityprojects.citybackend.dto.hebergement.TarifChambreCreateDto;
import com.cityprojects.citybackend.dto.hebergement.TarifChambreDto;
import com.cityprojects.citybackend.entity.core.Hotel;
import com.cityprojects.citybackend.entity.hebergement.TypeChambre;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.exception.ResourceNotFoundException;
import com.cityprojects.citybackend.repository.core.HotelRepository;
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

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests Surefire (rapides, en H2) du {@link TarifChambreService} - Tour 44 Phase 1.
 *
 * <h3>Couverture</h3>
 * <ol>
 *   <li>T1 : create() persiste un tarif dans le tenant courant et retourne
 *       le DTO complet.</li>
 *   <li>T2 : getPrixForDate() avec un tarif actif applicable -&gt; prix du tarif.
 *       Sans tarif applicable -&gt; fallback {@code TypeChambre.prixBase}.</li>
 *   <li>T3 : calculer() sur 3 nuits applique le prix saisonnier sur chaque jour
 *       et retourne le montant total + detail.</li>
 *   <li>T4 : multi-tenant - tarif cree pour hotel A invisible depuis hotel B
 *       ({@link ResourceNotFoundException} sur findById).</li>
 * </ol>
 */
@SpringBootTest
@ActiveProfiles("test")
class TarifChambreServiceTests {

    @Autowired
    private TarifChambreService tarifChambreService;

    @Autowired
    private HotelRepository hotelRepository;

    @Autowired
    private TypeChambreRepository typeChambreRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;
    private Long hotelMrId;
    private Long hotelFrId;
    private Long typeChambreMrId;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        TenantContext.clear();

        // Cleanup ordonne (FK)
        jdbcTemplate.update("DELETE FROM hebergement.tarifs_chambres");
        jdbcTemplate.update("DELETE FROM hebergement.types_chambres");
        jdbcTemplate.update("DELETE FROM core.hotels");

        Hotel mr = new Hotel("MRH001", "Hotel Mauritanie");
        mr.setCodePays("MR");
        hotelMrId = hotelRepository.saveAndFlush(mr).getHotelId();

        Hotel fr = new Hotel("FRH001", "Hotel France");
        fr.setCodePays("FR");
        hotelFrId = hotelRepository.saveAndFlush(fr).getHotelId();

        // TypeChambre cote MR avec prixBase 80
        try {
            TenantContext.set(hotelMrId);
            TypeChambre type = transactionTemplate.execute(s -> {
                TypeChambre t = new TypeChambre();
                t.setTypeCode("STD");
                t.setTypeNom("Standard");
                t.setNbLitsMax(2);
                t.setNbPersonnesMax(2);
                t.setPrixBase(new BigDecimal("80.00"));
                t.setActif(Boolean.TRUE);
                return typeChambreRepository.save(t);
            });
            typeChambreMrId = type.getTypeId();
        } finally {
            TenantContext.clear();
        }
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        jdbcTemplate.update("DELETE FROM hebergement.tarifs_chambres");
        jdbcTemplate.update("DELETE FROM hebergement.types_chambres");
        jdbcTemplate.update("DELETE FROM core.hotels");
    }

    @Test
    @DisplayName("T1 - create() persiste un tarif et retourne le DTO")
    void shouldCreateTarif() {
        TenantContext.set(hotelMrId);

        TarifChambreCreateDto dto = new TarifChambreCreateDto(
                typeChambreMrId, "Haute saison",
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 8, 31),
                new BigDecimal("150.00"), new BigDecimal("180.00"), 10, Boolean.TRUE);

        TarifChambreDto created = transactionTemplate.execute(s -> tarifChambreService.create(dto));

        assertNotNull(created);
        assertNotNull(created.tarifId());
        assertEquals("Haute saison", created.nomTarif());
        assertEquals(0, created.prixNuit().compareTo(new BigDecimal("150.00")));
        assertEquals(0, created.prixWeekend().compareTo(new BigDecimal("180.00")));
        assertEquals(10, created.priorite());
        assertTrue(created.actif());
    }

    @Test
    @DisplayName("T2 - getPrixForDate() retourne le tarif applicable, sinon prixBase")
    void shouldReturnPrixFromTarifOrFallbackPrixBase() {
        TenantContext.set(hotelMrId);

        // Cree un tarif "Haute saison" 150 MRU du 1er juillet au 31 aout
        TarifChambreCreateDto dto = new TarifChambreCreateDto(
                typeChambreMrId, "Haute saison",
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 8, 31),
                new BigDecimal("150.00"), null, 0, Boolean.TRUE);
        transactionTemplate.execute(s -> tarifChambreService.create(dto));

        // Dans la periode -> 150
        BigDecimal prixIn = transactionTemplate.execute(s ->
                tarifChambreService.getPrixForDate(typeChambreMrId, LocalDate.of(2026, 7, 15)));
        assertEquals(0, prixIn.compareTo(new BigDecimal("150.00")),
                "Tarif applicable doit etre retourne");

        // Hors periode -> fallback prixBase 80
        BigDecimal prixOut = transactionTemplate.execute(s ->
                tarifChambreService.getPrixForDate(typeChambreMrId, LocalDate.of(2026, 9, 15)));
        assertEquals(0, prixOut.compareTo(new BigDecimal("80.00")),
                "Fallback prixBase TypeChambre");
    }

    @Test
    @DisplayName("T3 - calculer() somme le prix de chaque nuit du sejour")
    void shouldCalculerMontantTotal() {
        TenantContext.set(hotelMrId);

        // Tarif 100 du 1er juin au 30 septembre (priorite 0)
        TarifChambreCreateDto dto = new TarifChambreCreateDto(
                typeChambreMrId, "Standard ete",
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 9, 30),
                new BigDecimal("100.00"), null, 0, Boolean.TRUE);
        transactionTemplate.execute(s -> tarifChambreService.create(dto));

        // Sejour 3 nuits dans la periode (15 -> 18 juillet 2026)
        MontantCalculDto res = transactionTemplate.execute(s ->
                tarifChambreService.calculer(typeChambreMrId,
                        LocalDate.of(2026, 7, 15), LocalDate.of(2026, 7, 18)));

        assertEquals(typeChambreMrId, res.typeChambreId());
        assertEquals(3, res.totalNuits());
        assertEquals(0, res.montantHt().compareTo(new BigDecimal("300.00")));
        assertEquals(0, res.montantTtc().compareTo(new BigDecimal("300.00")),
                "Pas de TVA palier 1, TTC == HT");
        assertEquals(3, res.detail().size());
        // Toutes les origines doivent etre TARIF:Standard ete (pas weekend, prixWeekend null)
        for (var d : res.detail()) {
            assertTrue(d.origine().startsWith("TARIF:"),
                    "Origine doit etre TARIF: pour ce sejour - vu : " + d.origine());
            assertEquals(0, d.prix().compareTo(new BigDecimal("100.00")));
        }
    }

    @Test
    @DisplayName("T4 - Multi-tenant : tarif cree pour MR invisible depuis FR")
    void shouldIsolateAcrossTenants() {
        TenantContext.set(hotelMrId);

        TarifChambreCreateDto dto = new TarifChambreCreateDto(
                typeChambreMrId, "MR seulement",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                new BigDecimal("100.00"), null, 0, Boolean.TRUE);
        TarifChambreDto created = transactionTemplate.execute(s -> tarifChambreService.create(dto));

        // Bascule hotel FR : tarif doit etre invisible (TenantId filter Hibernate)
        TenantContext.set(hotelFrId);
        assertThrows(ResourceNotFoundException.class,
                () -> transactionTemplate.execute(s -> tarifChambreService.findById(created.tarifId())),
                "Le tarif d'un autre hotel doit etre invisible");
    }

    @Test
    @DisplayName("T5 - calculer() refuse dateFin <= dateDebut (BusinessException)")
    void shouldRejectInvalidDates() {
        TenantContext.set(hotelMrId);
        assertThrows(BusinessException.class,
                () -> transactionTemplate.execute(s ->
                        tarifChambreService.calculer(typeChambreMrId,
                                LocalDate.of(2026, 7, 15), LocalDate.of(2026, 7, 15))));
    }

    /**
     * Helper : retourne le prochain samedi >= ref (utilise pour controler le
     * weekend dans des assertions plus elaborees - non utilise dans T1-T5).
     */
    @SuppressWarnings("unused")
    private LocalDate nextSaturday(LocalDate ref) {
        return ref.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));
    }
}

package com.cityprojects.citybackend.service.finance;

import com.cityprojects.citybackend.dto.finance.TauxTvaConfigDto;
import com.cityprojects.citybackend.entity.finance.TauxTvaConfig;
import com.cityprojects.citybackend.entity.finance.TypeServiceTva;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.repository.finance.TauxTvaConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests Surefire (Mockito) du {@link TauxTvaConfigService} B4.
 */
class TauxTvaConfigServiceTests {

    private TauxTvaConfigRepository repository;
    private TauxTvaConfigServiceImpl service;

    @BeforeEach
    void setUp() {
        repository = mock(TauxTvaConfigRepository.class);
        service = new TauxTvaConfigServiceImpl(repository);
    }

    @Test
    @DisplayName("T1 - getTaux fallback sur defaut code si aucune config")
    void shouldFallbackToDefault() {
        when(repository.findByTypeService(TypeServiceTva.RESTAURATION))
                .thenReturn(Optional.empty());
        BigDecimal taux = service.getTaux(TypeServiceTva.RESTAURATION);
        assertEquals(0, taux.compareTo(new BigDecimal("16.00")));
    }

    @Test
    @DisplayName("T2 - getTaux fallback sur defaut si actif=false")
    void shouldFallbackWhenInactif() {
        TauxTvaConfig cfg = new TauxTvaConfig();
        cfg.setTypeService(TypeServiceTva.RESTAURATION);
        cfg.setTaux(new BigDecimal("10.00"));
        cfg.setActif(Boolean.FALSE);
        when(repository.findByTypeService(TypeServiceTva.RESTAURATION))
                .thenReturn(Optional.of(cfg));
        BigDecimal taux = service.getTaux(TypeServiceTva.RESTAURATION);
        // Fallback sur le defaut, pas sur la valeur inactive
        assertEquals(0, taux.compareTo(new BigDecimal("16.00")));
    }

    @Test
    @DisplayName("T3 - getTaux renvoie la config personnalisee si actif=true")
    void shouldUseCustomTaux() {
        TauxTvaConfig cfg = new TauxTvaConfig();
        cfg.setTypeService(TypeServiceTva.HEBERGEMENT_NUITEE);
        cfg.setTaux(new BigDecimal("16.00"));  // surcharge a 16% sur les nuitees
        cfg.setActif(Boolean.TRUE);
        when(repository.findByTypeService(TypeServiceTva.HEBERGEMENT_NUITEE))
                .thenReturn(Optional.of(cfg));
        BigDecimal taux = service.getTaux(TypeServiceTva.HEBERGEMENT_NUITEE);
        assertEquals(0, taux.compareTo(new BigDecimal("16.00")));
    }

    @Test
    @DisplayName("T4 - getTaux : type null -> 0 (defensif, ne plante pas)")
    void shouldReturnZeroForNullType() {
        assertEquals(0, service.getTaux(null).compareTo(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("T5 - HEBERGEMENT_NUITEE defaut = 0% (exoneration mauritanienne)")
    void hebergementDefaultZero() {
        when(repository.findByTypeService(TypeServiceTva.HEBERGEMENT_NUITEE))
                .thenReturn(Optional.empty());
        assertEquals(0, service.getTaux(TypeServiceTva.HEBERGEMENT_NUITEE)
                .compareTo(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("T6 - findAll inclut tous les types (defauts + personnalises)")
    void shouldListAllTypes() {
        TauxTvaConfig persiste = new TauxTvaConfig();
        persiste.setTypeService(TypeServiceTva.RESTAURATION);
        persiste.setTaux(new BigDecimal("12.00"));
        persiste.setActif(Boolean.TRUE);
        persiste.setLibelle("Custom resto");
        when(repository.findAllByOrderByTypeServiceAsc()).thenReturn(List.of(persiste));

        List<TauxTvaConfigDto> all = service.findAll();
        assertEquals(TypeServiceTva.values().length, all.size());
        TauxTvaConfigDto resto = all.stream()
                .filter(d -> d.typeService() == TypeServiceTva.RESTAURATION)
                .findFirst().orElseThrow();
        assertEquals(0, resto.taux().compareTo(new BigDecimal("12.00")));
        assertFalse(resto.defaut());
        // HEBERGEMENT_NUITEE : non persiste -> defaut synthetique
        TauxTvaConfigDto nuit = all.stream()
                .filter(d -> d.typeService() == TypeServiceTva.HEBERGEMENT_NUITEE)
                .findFirst().orElseThrow();
        assertTrue(nuit.defaut());
        assertEquals(0, nuit.taux().compareTo(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("T7 - update cree une nouvelle config si absente")
    void shouldCreateOnUpdate() {
        when(repository.findByTypeService(TypeServiceTva.BAR))
                .thenReturn(Optional.empty());
        when(repository.save(any(TauxTvaConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        TauxTvaConfigDto dto = service.update(TypeServiceTva.BAR,
                new BigDecimal("18.00"), null, "Bar special");
        assertEquals(TypeServiceTva.BAR, dto.typeService());
        assertEquals(0, dto.taux().compareTo(new BigDecimal("18.00")));
        assertTrue(dto.actif());
        assertEquals("Bar special", dto.libelle());
        verify(repository).save(any(TauxTvaConfig.class));
    }

    @Test
    @DisplayName("T8 - update met a jour la config existante")
    void shouldUpdateExisting() {
        TauxTvaConfig existing = new TauxTvaConfig();
        existing.setTypeService(TypeServiceTva.BAR);
        existing.setTaux(new BigDecimal("16.00"));
        existing.setActif(Boolean.TRUE);
        existing.setLibelle("Old libelle");
        when(repository.findByTypeService(TypeServiceTva.BAR))
                .thenReturn(Optional.of(existing));
        when(repository.save(any(TauxTvaConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        TauxTvaConfigDto dto = service.update(TypeServiceTva.BAR,
                new BigDecimal("20.00"), Boolean.FALSE, "New libelle");
        assertEquals(0, dto.taux().compareTo(new BigDecimal("20.00")));
        assertFalse(dto.actif());
        assertEquals("New libelle", dto.libelle());
    }

    @Test
    @DisplayName("T9 - update refuse taux negatif")
    void shouldRejectNegativeTaux() {
        assertThrows(BusinessException.class, () ->
                service.update(TypeServiceTva.BAR, new BigDecimal("-1.00"), null, null));
    }

    @Test
    @DisplayName("T10 - update refuse taux >= 100")
    void shouldRejectTooHighTaux() {
        assertThrows(BusinessException.class, () ->
                service.update(TypeServiceTva.BAR, new BigDecimal("100.00"), null, null));
    }

    @Test
    @DisplayName("T11 - update refuse type null")
    void shouldRejectNullType() {
        assertThrows(BusinessException.class, () ->
                service.update(null, new BigDecimal("10.00"), null, null));
    }

    @Test
    @DisplayName("T12 - update refuse taux null")
    void shouldRejectNullTaux() {
        assertThrows(BusinessException.class, () ->
                service.update(TypeServiceTva.BAR, null, null, null));
    }
}

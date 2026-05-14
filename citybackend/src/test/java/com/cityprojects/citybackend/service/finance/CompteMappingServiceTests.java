package com.cityprojects.citybackend.service.finance;

import com.cityprojects.citybackend.dto.finance.CompteMappingDto;
import com.cityprojects.citybackend.entity.finance.CompteMapping;
import com.cityprojects.citybackend.entity.finance.PlanComptableGeneral;
import com.cityprojects.citybackend.entity.finance.TypeEvenementComptable;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.repository.finance.CompteMappingRepository;
import com.cityprojects.citybackend.repository.finance.PlanComptableGeneralRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests Surefire (Mockito leger) du {@link CompteMappingService}.
 *
 * <h3>Couverture</h3>
 * <ol>
 *   <li>T1 - {@code getCompte} sans mapping personnalise -&gt; renvoie le defaut
 *       code par {@link TypeEvenementComptable#defaultCompteCode()}.</li>
 *   <li>T2 - {@code getCompte} avec mapping personnalise actif -&gt; renvoie le
 *       code en base.</li>
 *   <li>T3 - {@code getCompte} avec mapping inactif -&gt; fallback sur defaut.</li>
 *   <li>T4 - {@code updateMapping} avec code inexistant dans le PCG -&gt;
 *       {@link BusinessException("error.mapping.invalidCompte")}.</li>
 *   <li>T5 - {@code updateMapping} valide -&gt; cree ou met a jour la ligne.</li>
 *   <li>T6 - {@code listAll} : merge des mappings persistes + defauts codes
 *       pour les types non encore mappes ; nombre de DTO = nombre d'enum values.</li>
 * </ol>
 */
class CompteMappingServiceTests {

    private CompteMappingRepository mappingRepository;
    private PlanComptableGeneralRepository pcgRepository;
    private CompteMappingServiceImpl service;

    @BeforeEach
    void setUp() {
        mappingRepository = mock(CompteMappingRepository.class);
        pcgRepository = mock(PlanComptableGeneralRepository.class);
        service = new CompteMappingServiceImpl(mappingRepository, pcgRepository);
    }

    @Test
    @DisplayName("T1 - getCompte sans mapping personnalise renvoie le defaut code")
    void getCompteFallback() {
        when(mappingRepository.findByTypeEvenement(TypeEvenementComptable.VENTE_NUITEE_HEBERGEMENT))
                .thenReturn(Optional.empty());

        String code = service.getCompte(TypeEvenementComptable.VENTE_NUITEE_HEBERGEMENT);
        // Defaut codé dans l'enum
        assertEquals(TypeEvenementComptable.VENTE_NUITEE_HEBERGEMENT.defaultCompteCode(), code);
        assertEquals("706100", code);
    }

    @Test
    @DisplayName("T2 - getCompte avec mapping personnalise actif renvoie le code en base")
    void getCompteCustom() {
        CompteMapping mapping = new CompteMapping();
        mapping.setTypeEvenement(TypeEvenementComptable.VENTE_RESTAURATION);
        mapping.setCompteCode("706201");
        mapping.setActif(Boolean.TRUE);
        when(mappingRepository.findByTypeEvenement(TypeEvenementComptable.VENTE_RESTAURATION))
                .thenReturn(Optional.of(mapping));

        assertEquals("706201", service.getCompte(TypeEvenementComptable.VENTE_RESTAURATION));
    }

    @Test
    @DisplayName("T3 - getCompte avec mapping inactif fallback sur defaut")
    void getCompteInactiveFallback() {
        CompteMapping mapping = new CompteMapping();
        mapping.setTypeEvenement(TypeEvenementComptable.VENTE_BAR);
        mapping.setCompteCode("999999");
        mapping.setActif(Boolean.FALSE);
        when(mappingRepository.findByTypeEvenement(TypeEvenementComptable.VENTE_BAR))
                .thenReturn(Optional.of(mapping));

        String code = service.getCompte(TypeEvenementComptable.VENTE_BAR);
        assertEquals("706300", code);
    }

    @Test
    @DisplayName("T4 - updateMapping avec code inexistant leve BusinessException invalidCompte")
    void updateMappingInvalidCode() {
        when(pcgRepository.existsUtilisableByCode("XYZ")).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.updateMapping(TypeEvenementComptable.VENTE_BAR, "XYZ"));
        assertEquals("error.mapping.invalidCompte", ex.getMessage());
        verify(mappingRepository, never()).save(any());
    }

    @Test
    @DisplayName("T5 - updateMapping valide cree la ligne avec le compte cible")
    void updateMappingHappyPath() {
        when(pcgRepository.existsUtilisableByCode("706350")).thenReturn(true);
        when(mappingRepository.findByTypeEvenement(TypeEvenementComptable.VENTE_BAR))
                .thenReturn(Optional.empty());
        when(mappingRepository.save(any(CompteMapping.class))).thenAnswer(inv -> inv.getArgument(0));
        when(pcgRepository.findByCompteCode("706350"))
                .thenReturn(Optional.of(pcg("706350", "Bar - happy hour")));

        CompteMappingDto dto = service.updateMapping(TypeEvenementComptable.VENTE_BAR, "706350");

        assertEquals(TypeEvenementComptable.VENTE_BAR, dto.typeEvenement());
        assertEquals("706350", dto.compteCode());
        assertEquals("Bar - happy hour", dto.compteLibelle());
        assertTrue(dto.actif());
        assertFalse(dto.defaut());

        ArgumentCaptor<CompteMapping> captor = ArgumentCaptor.forClass(CompteMapping.class);
        verify(mappingRepository).save(captor.capture());
        CompteMapping saved = captor.getValue();
        assertEquals("706350", saved.getCompteCode());
        assertEquals(Boolean.TRUE, saved.getActif());
    }

    @Test
    @DisplayName("T6 - listAll merge mappings persistes + defauts codes (size = nombre d'enum values)")
    void listAllMergesDefaults() {
        // Un seul mapping personnalise pour TRESORERIE_ESPECES, les autres = defauts
        CompteMapping personnalise = new CompteMapping();
        personnalise.setTypeEvenement(TypeEvenementComptable.TRESORERIE_ESPECES);
        personnalise.setCompteCode("531190");
        personnalise.setActif(Boolean.TRUE);
        when(mappingRepository.findAllByOrderByTypeEvenementAsc())
                .thenReturn(List.of(personnalise));
        // PCG : tout libelle vaut "" (non setup)
        when(pcgRepository.findByCompteCode(any())).thenReturn(Optional.empty());

        List<CompteMappingDto> all = service.listAll();
        assertNotNull(all);
        assertEquals(TypeEvenementComptable.values().length, all.size());

        // Verifier que TRESORERIE_ESPECES est personnalise (defaut=false)
        CompteMappingDto especes = all.stream()
                .filter(d -> d.typeEvenement() == TypeEvenementComptable.TRESORERIE_ESPECES)
                .findFirst().orElseThrow();
        assertEquals("531190", especes.compteCode());
        assertFalse(especes.defaut());

        // Verifier qu'un autre type a son defaut code
        CompteMappingDto bar = all.stream()
                .filter(d -> d.typeEvenement() == TypeEvenementComptable.VENTE_BAR)
                .findFirst().orElseThrow();
        assertEquals("706300", bar.compteCode());
        assertTrue(bar.defaut());
    }

    private static PlanComptableGeneral pcg(String code, String libelle) {
        PlanComptableGeneral p = new PlanComptableGeneral();
        p.setCompteCode(code);
        p.setLibelle(libelle);
        return p;
    }
}

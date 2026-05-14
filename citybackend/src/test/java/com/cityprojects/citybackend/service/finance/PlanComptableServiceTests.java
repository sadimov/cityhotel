package com.cityprojects.citybackend.service.finance;

import com.cityprojects.citybackend.dto.finance.PlanComptableGeneralDto;
import com.cityprojects.citybackend.entity.finance.NatureCompte;
import com.cityprojects.citybackend.entity.finance.PlanComptableGeneral;
import com.cityprojects.citybackend.entity.finance.SensNormal;
import com.cityprojects.citybackend.entity.finance.StatutCompteComptable;
import com.cityprojects.citybackend.exception.ResourceNotFoundException;
import com.cityprojects.citybackend.mapper.finance.PlanComptableMapper;
import com.cityprojects.citybackend.mapper.finance.PlanComptableMapperImpl;
import com.cityprojects.citybackend.repository.finance.PlanComptableGeneralRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests Surefire (Mockito leger) du {@link PlanComptableService}.
 *
 * <h3>Couverture</h3>
 * <ol>
 *   <li>T1 - {@code findByCode} : succes (entite -&gt; DTO).</li>
 *   <li>T2 - {@code findByCode} : code inconnu -&gt; {@link ResourceNotFoundException}.</li>
 *   <li>T3 - {@code findAll(true, ...)} : route vers {@code findUtilisables}
 *       avec {@code statut = ACTIF}.</li>
 *   <li>T4 - {@code findAll(false, ...)} : route vers {@code findAll}
 *       standard (toutes les lignes, regroupements inclus).</li>
 * </ol>
 */
class PlanComptableServiceTests {

    private PlanComptableGeneralRepository repository;
    private PlanComptableMapper mapper;
    private PlanComptableServiceImpl service;

    @BeforeEach
    void setUp() {
        repository = mock(PlanComptableGeneralRepository.class);
        mapper = new PlanComptableMapperImpl();
        service = new PlanComptableServiceImpl(repository, mapper);
    }

    private static PlanComptableGeneral compte(String code, String libelle, int classe,
                                               NatureCompte nature, SensNormal sens,
                                               boolean utilisable) {
        PlanComptableGeneral pcg = new PlanComptableGeneral();
        pcg.setCompteCode(code);
        pcg.setLibelle(libelle);
        pcg.setClasse(classe);
        pcg.setNature(nature);
        pcg.setSensNormal(sens);
        pcg.setUtilisable(utilisable);
        pcg.setStatut(StatutCompteComptable.ACTIF);
        return pcg;
    }

    @Test
    @DisplayName("T1 - findByCode renvoie le DTO du compte")
    void findByCodeShouldReturnDto() {
        PlanComptableGeneral pcg = compte("411100", "Clients particuliers", 4,
                NatureCompte.ACTIF, SensNormal.DEBITEUR, true);
        when(repository.findById("411100")).thenReturn(Optional.of(pcg));

        PlanComptableGeneralDto dto = service.findByCode("411100");

        assertEquals("411100", dto.compteCode());
        assertEquals("Clients particuliers", dto.libelle());
        assertEquals(4, dto.classe());
        assertEquals(NatureCompte.ACTIF, dto.nature());
        assertEquals(SensNormal.DEBITEUR, dto.sensNormal());
    }

    @Test
    @DisplayName("T2 - findByCode code inconnu leve ResourceNotFoundException")
    void findByCodeUnknownShouldThrow() {
        when(repository.findById("999999")).thenReturn(Optional.empty());
        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> service.findByCode("999999"));
        // Cle i18n (pas un message traduit)
        assertEquals("error.plancomptable.notFound", ex.getMessage());
    }

    @Test
    @DisplayName("T3 - findAll(utilisableOnly=true) route vers findUtilisables")
    void findAllUtilisableOnly() {
        PlanComptableGeneral pcg = compte("706100", "Ventes nuitees", 7,
                NatureCompte.PRODUIT, SensNormal.CREDITEUR, true);
        Page<PlanComptableGeneral> page = new PageImpl<>(List.of(pcg));
        when(repository.findUtilisables(eq(StatutCompteComptable.ACTIF), any(Pageable.class)))
                .thenReturn(page);

        Page<PlanComptableGeneralDto> result = service.findAll(true, Pageable.unpaged());
        assertEquals(1, result.getTotalElements());
        assertEquals("706100", result.getContent().get(0).compteCode());
    }

    @Test
    @DisplayName("T4 - findAll(utilisableOnly=false) route vers findAll standard")
    void findAllIncludingNonUsable() {
        PlanComptableGeneral racine = compte("100000", "Comptes de ressources durables", 1,
                NatureCompte.PASSIF, SensNormal.CREDITEUR, false);
        PlanComptableGeneral feuille = compte("101100", "Capital social", 1,
                NatureCompte.PASSIF, SensNormal.CREDITEUR, true);
        Page<PlanComptableGeneral> page = new PageImpl<>(List.of(racine, feuille));
        when(repository.findAll(any(Pageable.class))).thenReturn(page);

        Page<PlanComptableGeneralDto> result = service.findAll(false, Pageable.unpaged());
        assertEquals(2, result.getTotalElements());
    }
}

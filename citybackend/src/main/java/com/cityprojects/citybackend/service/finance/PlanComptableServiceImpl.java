package com.cityprojects.citybackend.service.finance;

import com.cityprojects.citybackend.dto.finance.PlanComptableGeneralDto;
import com.cityprojects.citybackend.entity.finance.StatutCompteComptable;
import com.cityprojects.citybackend.exception.ResourceNotFoundException;
import com.cityprojects.citybackend.mapper.finance.PlanComptableMapper;
import com.cityprojects.citybackend.repository.finance.PlanComptableGeneralRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implémentation de {@link PlanComptableService}.
 *
 * <p>Service technique cross-tenant : <b>pas</b> de {@code @RequireTenant}.
 * Le PCG est un référentiel global, lu de la même façon par tous les hôtels.</p>
 *
 * <p>Le service est en {@code readOnly = true} au niveau classe : aucune
 * mutation n'est exposée à l'API. Les évolutions du PCG passent par des
 * migrations Liquibase (cf. {@code 039-create-plan-comptable-general.xml}).</p>
 */
@Service
@Transactional(readOnly = true)
public class PlanComptableServiceImpl implements PlanComptableService {

    private final PlanComptableGeneralRepository repository;
    private final PlanComptableMapper mapper;

    public PlanComptableServiceImpl(PlanComptableGeneralRepository repository,
                                    PlanComptableMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Page<PlanComptableGeneralDto> findAll(boolean utilisableOnly, Pageable pageable) {
        if (utilisableOnly) {
            return repository.findUtilisables(StatutCompteComptable.ACTIF, pageable)
                    .map(mapper::toDto);
        }
        return repository.findAll(pageable).map(mapper::toDto);
    }

    @Override
    public PlanComptableGeneralDto findByCode(String compteCode) {
        return repository.findById(compteCode)
                .map(mapper::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("error.plancomptable.notFound"));
    }
}

package com.cityprojects.citybackend.mapper.finance;

import com.cityprojects.citybackend.dto.finance.PlanComptableGeneralDto;
import com.cityprojects.citybackend.entity.finance.PlanComptableGeneral;
import org.mapstruct.Mapper;

/**
 * Mapper MapStruct {@link PlanComptableGeneral} &lt;-&gt;
 * {@link PlanComptableGeneralDto}. Pas de logique metier : conversion
 * structurelle uniquement.
 */
@Mapper(componentModel = "spring")
public interface PlanComptableMapper {

    PlanComptableGeneralDto toDto(PlanComptableGeneral entity);
}

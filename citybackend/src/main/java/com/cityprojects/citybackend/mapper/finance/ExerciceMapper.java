package com.cityprojects.citybackend.mapper.finance;

import com.cityprojects.citybackend.dto.finance.ExerciceDto;
import com.cityprojects.citybackend.entity.finance.Exercice;
import org.mapstruct.Mapper;

/**
 * Mapper MapStruct {@link Exercice} -&gt; {@link ExerciceDto}. Pas de logique
 * metier - conversion structurelle uniquement.
 */
@Mapper(componentModel = "spring")
public interface ExerciceMapper {

    ExerciceDto toDto(Exercice entity);
}

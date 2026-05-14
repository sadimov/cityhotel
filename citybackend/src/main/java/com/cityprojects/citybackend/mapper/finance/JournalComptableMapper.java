package com.cityprojects.citybackend.mapper.finance;

import com.cityprojects.citybackend.dto.finance.JournalComptableDto;
import com.cityprojects.citybackend.entity.finance.JournalComptable;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Mapper MapStruct {@link JournalComptable} -&gt; {@link JournalComptableDto}.
 * Pas de logique metier - conversion structurelle uniquement.
 */
@Mapper(componentModel = "spring")
public interface JournalComptableMapper {

    @Mapping(target = "actif", expression = "java(entity.isActif())")
    JournalComptableDto toDto(JournalComptable entity);
}

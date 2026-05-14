package com.cityprojects.citybackend.mapper.finance;

import com.cityprojects.citybackend.dto.finance.EcritureComptableDto;
import com.cityprojects.citybackend.dto.finance.LigneEcritureDto;
import com.cityprojects.citybackend.entity.finance.EcritureComptable;
import com.cityprojects.citybackend.entity.finance.LigneEcriture;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;

/**
 * Mapper MapStruct {@link EcritureComptable} -&gt; {@link EcritureComptableDto}.
 *
 * <p>Pas de logique metier - conversion structurelle uniquement. La
 * denormalisation des references (journal code/libelle, exercice code) est
 * realisee via les expressions ci-dessous.</p>
 */
@Mapper(componentModel = "spring")
public interface EcritureComptableMapper {

    @Mapping(target = "journalId", expression = "java(entity.getJournal() != null ? entity.getJournal().getId() : null)")
    @Mapping(target = "journalCode", expression = "java(entity.getJournal() != null ? entity.getJournal().getCode() : null)")
    @Mapping(target = "journalLibelle", expression = "java(entity.getJournal() != null ? entity.getJournal().getLibelle() : null)")
    @Mapping(target = "exerciceId", expression = "java(entity.getExercice() != null ? entity.getExercice().getId() : null)")
    @Mapping(target = "exerciceCode", expression = "java(entity.getExercice() != null ? entity.getExercice().getCode() : null)")
    @Mapping(target = "lignes", source = "lignes", qualifiedByName = "lignesToDto")
    EcritureComptableDto toDto(EcritureComptable entity);

    @Named("lignesToDto")
    default List<LigneEcritureDto> lignesToDto(List<LigneEcriture> lignes) {
        if (lignes == null) {
            return List.of();
        }
        return lignes.stream()
                .map(this::ligneToDto)
                .toList();
    }

    LigneEcritureDto ligneToDto(LigneEcriture entity);
}

package com.cityprojects.citybackend.mapper.menage;

import com.cityprojects.citybackend.dto.menage.HistoriqueDto;
import com.cityprojects.citybackend.entity.menage.Historique;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Mapper MapStruct (lecture seule) pour
 * {@link com.cityprojects.citybackend.entity.menage.Historique}.
 *
 * <p>L'historique est ecrit exclusivement par {@code HistoriqueService} via un
 * appel direct - pas de DTO entrant.</p>
 */
@Mapper(componentModel = "spring")
public interface HistoriqueMapper {

    @Mapping(target = "nomPersonnel", ignore = true)
    @Mapping(target = "numeroChambre", ignore = true)
    HistoriqueDto toDto(Historique entity);
}

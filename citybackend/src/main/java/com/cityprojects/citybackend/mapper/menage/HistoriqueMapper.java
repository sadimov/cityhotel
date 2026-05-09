package com.cityprojects.citybackend.mapper.menage;

import com.cityprojects.citybackend.dto.menage.HistoriqueDto;
import com.cityprojects.citybackend.entity.menage.Historique;
import org.mapstruct.Mapper;

/**
 * Mapper MapStruct (lecture seule) pour
 * {@link com.cityprojects.citybackend.entity.menage.Historique}.
 *
 * <p>L'historique est ecrit exclusivement par {@code HistoriqueService} via un
 * appel direct - pas de DTO entrant.</p>
 */
@Mapper(componentModel = "spring")
public interface HistoriqueMapper {

    HistoriqueDto toDto(Historique entity);
}

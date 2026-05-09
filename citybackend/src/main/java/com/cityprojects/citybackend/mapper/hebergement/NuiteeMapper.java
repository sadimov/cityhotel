package com.cityprojects.citybackend.mapper.hebergement;

import com.cityprojects.citybackend.dto.hebergement.NuiteeDto;
import com.cityprojects.citybackend.entity.hebergement.Nuitee;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Mapper MapStruct entre {@link Nuitee} et {@link NuiteeDto}.
 *
 * <p>Aliases (Tour 14 audit B1) :</p>
 * <ul>
 *   <li>{@code nuiteeId} entite -&gt; {@code id} DTO ;</li>
 *   <li>{@code dateNuit} entite -&gt; {@code dateNuitee} DTO.</li>
 * </ul>
 *
 * <p>Le DTO ne contient PAS {@code hotelId} (resolu par Hibernate via
 * {@link org.hibernate.annotations.TenantId}).</p>
 */
@Mapper(componentModel = "spring")
public interface NuiteeMapper {

    @Mapping(target = "id", source = "nuiteeId")
    @Mapping(target = "dateNuitee", source = "dateNuit")
    NuiteeDto toDto(Nuitee entity);
}

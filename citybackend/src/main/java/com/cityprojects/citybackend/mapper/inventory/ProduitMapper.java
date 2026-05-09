package com.cityprojects.citybackend.mapper.inventory;

import com.cityprojects.citybackend.dto.inventory.ProduitCreateDto;
import com.cityprojects.citybackend.dto.inventory.ProduitDto;
import com.cityprojects.citybackend.entity.inventory.Produit;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Mapper MapStruct entre {@link Produit} et ses DTOs.
 *
 * <p>Les champs derives ({@link Produit#getValeurStock()},
 * {@link Produit#getStatutStock()}) sont mappes automatiquement par MapStruct
 * via les getters {@code @Transient} (le nom du champ DTO matche le getter).</p>
 */
@Mapper(componentModel = "spring")
public interface ProduitMapper {

    ProduitDto toDto(Produit entity);

    @Mapping(target = "produitId", ignore = true)
    @Mapping(target = "hotelId", ignore = true)
    @Mapping(target = "stockActuel", ignore = true)
    @Mapping(target = "actif", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    Produit toEntity(ProduitCreateDto dto);
}

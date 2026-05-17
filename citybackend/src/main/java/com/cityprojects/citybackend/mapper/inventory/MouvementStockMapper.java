package com.cityprojects.citybackend.mapper.inventory;

import com.cityprojects.citybackend.dto.inventory.MouvementStockDto;
import com.cityprojects.citybackend.entity.inventory.MouvementStock;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Mapper MapStruct pour {@link MouvementStock} (lecture seule, audit trail).
 *
 * <p>Pas de toEntity : les MouvementStock sont crees uniquement par les services
 * inventory (BC reception, BS livraison, ajustement manuel) - jamais via API directe.</p>
 */
@Mapper(componentModel = "spring")
public interface MouvementStockMapper {

    @Mapping(target = "nomProduit", ignore = true)
    @Mapping(target = "codeProduit", ignore = true)
    MouvementStockDto toDto(MouvementStock entity);
}

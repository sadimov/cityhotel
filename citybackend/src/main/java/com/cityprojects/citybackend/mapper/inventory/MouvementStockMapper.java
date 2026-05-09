package com.cityprojects.citybackend.mapper.inventory;

import com.cityprojects.citybackend.dto.inventory.MouvementStockDto;
import com.cityprojects.citybackend.entity.inventory.MouvementStock;
import org.mapstruct.Mapper;

/**
 * Mapper MapStruct pour {@link MouvementStock} (lecture seule, audit trail).
 *
 * <p>Pas de toEntity : les MouvementStock sont crees uniquement par les services
 * inventory (BC reception, BS livraison, ajustement manuel) - jamais via API directe.</p>
 */
@Mapper(componentModel = "spring")
public interface MouvementStockMapper {

    MouvementStockDto toDto(MouvementStock entity);
}

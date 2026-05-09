package com.cityprojects.citybackend.mapper.restaurant;

import com.cityprojects.citybackend.dto.restaurant.TicketDto;
import com.cityprojects.citybackend.entity.restaurant.Ticket;
import org.mapstruct.Mapper;

/**
 * Mapper MapStruct entre {@link Ticket} et son DTO de sortie (Tour 24).
 */
@Mapper(componentModel = "spring")
public interface TicketMapper {

    TicketDto toDto(Ticket entity);
}

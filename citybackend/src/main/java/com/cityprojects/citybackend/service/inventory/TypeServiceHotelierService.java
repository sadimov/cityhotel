package com.cityprojects.citybackend.service.inventory;

import com.cityprojects.citybackend.dto.inventory.TypeServiceHotelierCreateDto;
import com.cityprojects.citybackend.dto.inventory.TypeServiceHotelierDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Service de gestion des types de services hoteliers.
 */
public interface TypeServiceHotelierService {

    TypeServiceHotelierDto create(TypeServiceHotelierCreateDto dto);

    TypeServiceHotelierDto update(Long typeServiceId, TypeServiceHotelierCreateDto dto);

    TypeServiceHotelierDto findById(Long typeServiceId);

    Page<TypeServiceHotelierDto> search(String recherche, Pageable pageable);

    List<TypeServiceHotelierDto> findAllActive();

    void deactivate(Long typeServiceId);
}

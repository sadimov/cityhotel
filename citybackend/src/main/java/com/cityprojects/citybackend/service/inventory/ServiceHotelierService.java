package com.cityprojects.citybackend.service.inventory;

import com.cityprojects.citybackend.dto.inventory.ServiceHotelierCreateDto;
import com.cityprojects.citybackend.dto.inventory.ServiceHotelierDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Service de gestion des services hoteliers (prestations facturees au client).
 */
public interface ServiceHotelierService {

    ServiceHotelierDto create(ServiceHotelierCreateDto dto);

    ServiceHotelierDto update(Long serviceId, ServiceHotelierCreateDto dto);

    ServiceHotelierDto findById(Long serviceId);

    Page<ServiceHotelierDto> search(String recherche, Long typeServiceId, Pageable pageable);

    List<ServiceHotelierDto> findAllActive();

    void deactivate(Long serviceId);
}

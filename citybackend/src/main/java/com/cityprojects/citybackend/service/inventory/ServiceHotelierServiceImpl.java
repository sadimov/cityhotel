package com.cityprojects.citybackend.service.inventory;

import com.cityprojects.citybackend.common.tenant.RequireTenant;
import com.cityprojects.citybackend.dto.inventory.ServiceHotelierCreateDto;
import com.cityprojects.citybackend.dto.inventory.ServiceHotelierDto;
import com.cityprojects.citybackend.entity.inventory.ServiceHotelier;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.exception.ResourceNotFoundException;
import com.cityprojects.citybackend.mapper.inventory.ServiceHotelierMapper;
import com.cityprojects.citybackend.repository.inventory.ServiceHotelierRepository;
import com.cityprojects.citybackend.repository.inventory.TypeServiceHotelierRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Implementation de {@link ServiceHotelierService}.
 */
@Service
@RequireTenant
@Transactional(readOnly = true)
public class ServiceHotelierServiceImpl implements ServiceHotelierService {

    private static final Logger logger = LoggerFactory.getLogger(ServiceHotelierServiceImpl.class);

    private final ServiceHotelierRepository serviceRepository;
    private final TypeServiceHotelierRepository typeRepository;
    private final ServiceHotelierMapper mapper;

    public ServiceHotelierServiceImpl(ServiceHotelierRepository serviceRepository,
                                      TypeServiceHotelierRepository typeRepository,
                                      ServiceHotelierMapper mapper) {
        this.serviceRepository = serviceRepository;
        this.typeRepository = typeRepository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public ServiceHotelierDto create(ServiceHotelierCreateDto dto) {
        logger.info("Creation service hotelier : code={}, nom={}, typeId={}",
                dto.code(), dto.nom(), dto.typeServiceId());

        // Verifier que le type existe dans le tenant courant (Hibernate filtre auto).
        if (typeRepository.findById(dto.typeServiceId()).isEmpty()) {
            throw new ResourceNotFoundException("error.typeServiceHotelier.notFound");
        }
        if (serviceRepository.existsByCode(dto.code())) {
            throw new BusinessException("error.serviceHotelier.code.alreadyExists");
        }

        ServiceHotelier entity = mapper.toEntity(dto);
        entity.setActif(Boolean.TRUE);
        return mapper.toDto(serviceRepository.save(entity));
    }

    @Override
    @Transactional
    public ServiceHotelierDto update(Long serviceId, ServiceHotelierCreateDto dto) {
        logger.info("Modification service hotelier id={}", serviceId);
        ServiceHotelier entity = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("error.serviceHotelier.notFound"));

        // Si on change le type, verifier qu'il existe dans le tenant courant.
        if (!entity.getTypeServiceId().equals(dto.typeServiceId())) {
            if (typeRepository.findById(dto.typeServiceId()).isEmpty()) {
                throw new ResourceNotFoundException("error.typeServiceHotelier.notFound");
            }
            entity.setTypeServiceId(dto.typeServiceId());
        }

        // Code immuable apres creation (cle metier).
        entity.setNom(dto.nom());
        entity.setDescription(dto.description());
        entity.setPrixUnitaire(dto.prixUnitaire());
        entity.setUnite(dto.unite());
        return mapper.toDto(serviceRepository.save(entity));
    }

    @Override
    public ServiceHotelierDto findById(Long serviceId) {
        ServiceHotelier entity = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("error.serviceHotelier.notFound"));
        return mapper.toDto(entity);
    }

    @Override
    public Page<ServiceHotelierDto> search(String recherche, Long typeServiceId, Pageable pageable) {
        return serviceRepository.search(recherche, typeServiceId, pageable).map(mapper::toDto);
    }

    @Override
    public List<ServiceHotelierDto> findAllActive() {
        return serviceRepository.findByActifTrueOrderByNomAsc()
                .stream().map(mapper::toDto).toList();
    }

    @Override
    @Transactional
    public void deactivate(Long serviceId) {
        logger.info("Desactivation service hotelier id={}", serviceId);
        ServiceHotelier entity = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("error.serviceHotelier.notFound"));
        entity.setActif(Boolean.FALSE);
        serviceRepository.save(entity);
    }
}

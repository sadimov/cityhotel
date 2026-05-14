package com.cityprojects.citybackend.service.inventory;

import com.cityprojects.citybackend.common.tenant.RequireTenant;
import com.cityprojects.citybackend.dto.inventory.TypeServiceHotelierCreateDto;
import com.cityprojects.citybackend.dto.inventory.TypeServiceHotelierDto;
import com.cityprojects.citybackend.entity.inventory.TypeServiceHotelier;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.exception.ResourceNotFoundException;
import com.cityprojects.citybackend.mapper.inventory.TypeServiceHotelierMapper;
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
 * Implementation de {@link TypeServiceHotelierService}.
 */
@Service
@RequireTenant
@Transactional(readOnly = true)
public class TypeServiceHotelierServiceImpl implements TypeServiceHotelierService {

    private static final Logger logger = LoggerFactory.getLogger(TypeServiceHotelierServiceImpl.class);

    private final TypeServiceHotelierRepository typeRepository;
    private final ServiceHotelierRepository serviceRepository;
    private final TypeServiceHotelierMapper mapper;

    public TypeServiceHotelierServiceImpl(TypeServiceHotelierRepository typeRepository,
                                          ServiceHotelierRepository serviceRepository,
                                          TypeServiceHotelierMapper mapper) {
        this.typeRepository = typeRepository;
        this.serviceRepository = serviceRepository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public TypeServiceHotelierDto create(TypeServiceHotelierCreateDto dto) {
        logger.info("Creation type service hotelier : code={}, nom={}", dto.code(), dto.nom());
        if (typeRepository.existsByCode(dto.code())) {
            throw new BusinessException("error.typeServiceHotelier.code.alreadyExists");
        }
        TypeServiceHotelier entity = mapper.toEntity(dto);
        entity.setActif(Boolean.TRUE);
        return mapper.toDto(typeRepository.save(entity));
    }

    @Override
    @Transactional
    public TypeServiceHotelierDto update(Long typeServiceId, TypeServiceHotelierCreateDto dto) {
        logger.info("Modification type service hotelier id={}", typeServiceId);
        TypeServiceHotelier entity = typeRepository.findById(typeServiceId)
                .orElseThrow(() -> new ResourceNotFoundException("error.typeServiceHotelier.notFound"));

        // Code immuable apres creation (cle metier).
        entity.setNom(dto.nom());
        entity.setDescription(dto.description());
        return mapper.toDto(typeRepository.save(entity));
    }

    @Override
    public TypeServiceHotelierDto findById(Long typeServiceId) {
        TypeServiceHotelier entity = typeRepository.findById(typeServiceId)
                .orElseThrow(() -> new ResourceNotFoundException("error.typeServiceHotelier.notFound"));
        return mapper.toDto(entity);
    }

    @Override
    public Page<TypeServiceHotelierDto> search(String recherche, Pageable pageable) {
        return typeRepository.search(recherche, pageable).map(mapper::toDto);
    }

    @Override
    public List<TypeServiceHotelierDto> findAllActive() {
        return typeRepository.findByActifTrueOrderByNomAsc()
                .stream().map(mapper::toDto).toList();
    }

    @Override
    @Transactional
    public void deactivate(Long typeServiceId) {
        logger.info("Desactivation type service hotelier id={}", typeServiceId);
        TypeServiceHotelier entity = typeRepository.findById(typeServiceId)
                .orElseThrow(() -> new ResourceNotFoundException("error.typeServiceHotelier.notFound"));

        long actifs = serviceRepository.countByTypeServiceIdAndActifTrue(typeServiceId);
        if (actifs > 0) {
            throw new BusinessException("error.typeServiceHotelier.hasActiveServices");
        }
        entity.setActif(Boolean.FALSE);
        typeRepository.save(entity);
    }
}

package com.cityprojects.citybackend.service.hebergement;

import com.cityprojects.citybackend.common.paging.PageableUtils;
import com.cityprojects.citybackend.common.tenant.RequireTenant;
import com.cityprojects.citybackend.dto.hebergement.TypeChambreCreateDto;
import com.cityprojects.citybackend.dto.hebergement.TypeChambreDto;
import com.cityprojects.citybackend.entity.hebergement.CategorieEspace;
import com.cityprojects.citybackend.entity.hebergement.Chambre;
import com.cityprojects.citybackend.entity.hebergement.TypeChambre;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.exception.ResourceNotFoundException;
import com.cityprojects.citybackend.mapper.hebergement.TypeChambreMapper;
import com.cityprojects.citybackend.repository.hebergement.ChambreRepository;
import com.cityprojects.citybackend.repository.hebergement.TypeChambreRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Implementation de {@link TypeChambreService}.
 *
 * <p>Conventions :
 * <ul>
 *   <li>{@code @RequireTenant} au niveau classe.</li>
 *   <li>{@code @Transactional(readOnly = true)} a la classe, override en ecriture.</li>
 *   <li>Aucun {@code setHotelId} : Hibernate populate via le resolver.</li>
 * </ul>
 */
@Service
@RequireTenant
@Transactional(readOnly = true)
public class TypeChambreServiceImpl implements TypeChambreService {

    private static final Logger logger = LoggerFactory.getLogger(TypeChambreServiceImpl.class);

    private final TypeChambreRepository typeChambreRepository;
    private final ChambreRepository chambreRepository;
    private final TypeChambreMapper typeChambreMapper;

    public TypeChambreServiceImpl(TypeChambreRepository typeChambreRepository,
                                  ChambreRepository chambreRepository,
                                  TypeChambreMapper typeChambreMapper) {
        this.typeChambreRepository = typeChambreRepository;
        this.chambreRepository = chambreRepository;
        this.typeChambreMapper = typeChambreMapper;
    }

    @Override
    @Transactional
    public TypeChambreDto create(TypeChambreCreateDto dto) {
        logger.info("Creation type chambre : code={}, nom={}", dto.typeCode(), dto.typeNom());

        if (typeChambreRepository.existsByTypeCode(dto.typeCode())) {
            throw new BusinessException("error.typeChambre.code.alreadyExists");
        }

        TypeChambre entity = typeChambreMapper.toEntity(dto);
        entity.setActif(Boolean.TRUE);
        // Tour 49 : defaut CHAMBRE si categorie non fournie (ancienne API).
        if (entity.getCategorie() == null) {
            entity.setCategorie(CategorieEspace.CHAMBRE);
        }
        // PAS de setHotelId : Hibernate s'en charge.

        TypeChambre saved = typeChambreRepository.save(entity);
        return typeChambreMapper.toDto(saved);
    }

    @Override
    @Transactional
    public TypeChambreDto update(Long typeId, TypeChambreCreateDto dto) {
        TypeChambre entity = typeChambreRepository.findById(typeId)
                .orElseThrow(() -> new ResourceNotFoundException("error.typeChambre.notFound"));

        // Code modifie : verifier unicite hors du type courant
        if (!entity.getTypeCode().equals(dto.typeCode())
                && typeChambreRepository.existsByTypeCode(dto.typeCode())) {
            throw new BusinessException("error.typeChambre.code.alreadyExists");
        }

        entity.setTypeCode(dto.typeCode());
        entity.setTypeNom(dto.typeNom());
        entity.setDescription(dto.description());
        entity.setSuperficie(dto.superficie());
        entity.setNbLitsMax(dto.nbLitsMax());
        entity.setNbPersonnesMax(dto.nbPersonnesMax());
        entity.setPrixBase(dto.prixBase());
        // Tour 49 : update categorie si fournie (sinon on conserve l'existante).
        if (dto.categorie() != null) {
            entity.setCategorie(dto.categorie());
        }

        return typeChambreMapper.toDto(typeChambreRepository.save(entity));
    }

    @Override
    public TypeChambreDto findById(Long typeId) {
        TypeChambre entity = typeChambreRepository.findById(typeId)
                .orElseThrow(() -> new ResourceNotFoundException("error.typeChambre.notFound"));
        return typeChambreMapper.toDto(entity);
    }

    @Override
    public List<TypeChambreDto> findAllActive() {
        return typeChambreRepository.findByActifTrueOrderByTypeNomAsc().stream()
                .map(typeChambreMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public Page<TypeChambreDto> findAll(Boolean actif, Pageable pageable) {
        // Tri stable (Tour 14 audit, finding I2) + alias front "dateCreation" -> "createdAt"
        // (finding I4).
        Pageable remapped = PageableUtils.remapSort(pageable, Map.of("dateCreation", "createdAt"));
        Sort defaultSort = Sort.by(Sort.Order.asc("typeNom"));
        Pageable stable = PageableUtils.stable(remapped, defaultSort, "typeId");

        Page<TypeChambre> page = (actif != null)
                ? typeChambreRepository.findByActif(actif, stable)
                : typeChambreRepository.findAll(stable);
        return page.map(typeChambreMapper::toDto);
    }

    @Override
    @Transactional
    public void deactivate(Long typeId) {
        TypeChambre entity = typeChambreRepository.findById(typeId)
                .orElseThrow(() -> new ResourceNotFoundException("error.typeChambre.notFound"));

        // Refuser si chambres actives de ce type (eviter orphelinage metier)
        List<Chambre> chambres = chambreRepository.findByTypeIdAndActifTrueOrderByNumeroChambreAsc(typeId);
        if (!chambres.isEmpty()) {
            throw new BusinessException("error.typeChambre.hasActiveChambres");
        }

        entity.setActif(Boolean.FALSE);
        typeChambreRepository.save(entity);
    }

    @Override
    @Transactional
    public void reactivate(Long typeId) {
        TypeChambre entity = typeChambreRepository.findById(typeId)
                .orElseThrow(() -> new ResourceNotFoundException("error.typeChambre.notFound"));
        entity.setActif(Boolean.TRUE);
        typeChambreRepository.save(entity);
    }
}

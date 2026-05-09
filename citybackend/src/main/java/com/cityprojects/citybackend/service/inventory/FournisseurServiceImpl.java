package com.cityprojects.citybackend.service.inventory;

import com.cityprojects.citybackend.common.tenant.RequireTenant;
import com.cityprojects.citybackend.dto.inventory.FournisseurCreateDto;
import com.cityprojects.citybackend.dto.inventory.FournisseurDto;
import com.cityprojects.citybackend.entity.inventory.Fournisseur;
import com.cityprojects.citybackend.exception.ResourceNotFoundException;
import com.cityprojects.citybackend.mapper.inventory.FournisseurMapper;
import com.cityprojects.citybackend.repository.inventory.FournisseurRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Implementation de {@link FournisseurService}.
 */
@Service
@RequireTenant
@Transactional(readOnly = true)
public class FournisseurServiceImpl implements FournisseurService {

    private static final Logger logger = LoggerFactory.getLogger(FournisseurServiceImpl.class);

    private final FournisseurRepository fournisseurRepository;
    private final FournisseurMapper fournisseurMapper;

    public FournisseurServiceImpl(FournisseurRepository fournisseurRepository,
                                  FournisseurMapper fournisseurMapper) {
        this.fournisseurRepository = fournisseurRepository;
        this.fournisseurMapper = fournisseurMapper;
    }

    @Override
    @Transactional
    public FournisseurDto create(FournisseurCreateDto dto) {
        logger.info("Creation fournisseur : nom={}", dto.nomFournisseur());
        Fournisseur entity = fournisseurMapper.toEntity(dto);
        entity.setActif(Boolean.TRUE);
        // PAS de setHotelId : Hibernate s'en charge.
        Fournisseur saved = fournisseurRepository.save(entity);
        logger.info("Fournisseur cree : id={}", saved.getFournisseurId());
        return fournisseurMapper.toDto(saved);
    }

    @Override
    @Transactional
    public FournisseurDto update(Long fournisseurId, FournisseurCreateDto dto) {
        logger.info("Modification fournisseur id={}", fournisseurId);
        Fournisseur entity = fournisseurRepository.findById(fournisseurId)
                .orElseThrow(() -> new ResourceNotFoundException("error.fournisseur.notFound"));

        entity.setNomFournisseur(dto.nomFournisseur());
        entity.setContactPrincipal(dto.contactPrincipal());
        entity.setTelephone(dto.telephone());
        entity.setEmail(dto.email());
        entity.setAdresse(dto.adresse());
        entity.setVille(dto.ville());
        entity.setPays(dto.pays());
        entity.setConditionsPaiement(dto.conditionsPaiement());

        return fournisseurMapper.toDto(fournisseurRepository.save(entity));
    }

    @Override
    public FournisseurDto findById(Long fournisseurId) {
        Fournisseur entity = fournisseurRepository.findById(fournisseurId)
                .orElseThrow(() -> new ResourceNotFoundException("error.fournisseur.notFound"));
        return fournisseurMapper.toDto(entity);
    }

    @Override
    public Page<FournisseurDto> search(String recherche, Pageable pageable) {
        return fournisseurRepository.search(recherche, pageable).map(fournisseurMapper::toDto);
    }

    @Override
    public List<FournisseurDto> findAllActive() {
        return fournisseurRepository.findByActifTrueOrderByNomFournisseurAsc()
                .stream().map(fournisseurMapper::toDto).toList();
    }

    @Override
    @Transactional
    public void deactivate(Long fournisseurId) {
        logger.info("Desactivation fournisseur id={}", fournisseurId);
        Fournisseur entity = fournisseurRepository.findById(fournisseurId)
                .orElseThrow(() -> new ResourceNotFoundException("error.fournisseur.notFound"));
        entity.setActif(Boolean.FALSE);
        fournisseurRepository.save(entity);
    }
}

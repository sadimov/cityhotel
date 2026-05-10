package com.cityprojects.citybackend.service.admin;

import com.cityprojects.citybackend.dto.admin.ParametreAdminDto;
import com.cityprojects.citybackend.dto.admin.ParametreCreateAdminDto;
import com.cityprojects.citybackend.dto.admin.ParametreUpdateAdminDto;
import com.cityprojects.citybackend.entity.core.Parametre;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.exception.ResourceNotFoundException;
import com.cityprojects.citybackend.mapper.admin.ParametreAdminMapper;
import com.cityprojects.citybackend.repository.core.ParametreRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation de {@link ParametreAdminService}.
 *
 * <p>Pas de {@code @RequireTenant} (entite globale, securite par
 * {@code @PreAuthorize("hasRole('SUPERADMIN')")} controller).</p>
 */
@Service
@Transactional(readOnly = true)
public class ParametreAdminServiceImpl implements ParametreAdminService {

    private static final Logger logger = LoggerFactory.getLogger(ParametreAdminServiceImpl.class);

    private final ParametreRepository parametreRepository;
    private final ParametreAdminMapper parametreMapper;

    public ParametreAdminServiceImpl(ParametreRepository parametreRepository,
                                     ParametreAdminMapper parametreMapper) {
        this.parametreRepository = parametreRepository;
        this.parametreMapper = parametreMapper;
    }

    @Override
    @Transactional
    public ParametreAdminDto create(ParametreCreateAdminDto dto) {
        logger.info("Admin: creation parametre cle={}", dto.cle());
        if (parametreRepository.existsByCleIgnoreCase(dto.cle())) {
            throw new BusinessException("error.parametre.cle.alreadyExists");
        }
        Parametre entity = parametreMapper.toEntity(dto);
        // FORCE modifiable=true : les parametres systeme (modifiable=false)
        // ne sont creables que par changeset Liquibase.
        entity.setModifiable(Boolean.TRUE);
        Parametre saved = parametreRepository.save(entity);
        return parametreMapper.toDto(saved);
    }

    @Override
    @Transactional
    public ParametreAdminDto update(Long parametreId, ParametreUpdateAdminDto dto) {
        logger.info("Admin: update parametre id={}", parametreId);
        Parametre entity = parametreRepository.findById(parametreId)
                .orElseThrow(() -> new ResourceNotFoundException("error.parametre.notFound"));
        if (!Boolean.TRUE.equals(entity.getModifiable())) {
            throw new BusinessException("error.parametre.notModifiable");
        }
        parametreMapper.updateEntity(entity, dto);
        return parametreMapper.toDto(parametreRepository.save(entity));
    }

    @Override
    @Transactional
    public void delete(Long parametreId) {
        logger.info("Admin: delete parametre id={}", parametreId);
        Parametre entity = parametreRepository.findById(parametreId)
                .orElseThrow(() -> new ResourceNotFoundException("error.parametre.notFound"));
        if (!Boolean.TRUE.equals(entity.getModifiable())) {
            throw new BusinessException("error.parametre.notModifiable");
        }
        parametreRepository.delete(entity);
    }

    @Override
    public ParametreAdminDto findById(Long parametreId) {
        Parametre entity = parametreRepository.findById(parametreId)
                .orElseThrow(() -> new ResourceNotFoundException("error.parametre.notFound"));
        return parametreMapper.toDto(entity);
    }

    @Override
    public ParametreAdminDto findByCle(String cle) {
        Parametre entity = parametreRepository.findByCleIgnoreCase(cle)
                .orElseThrow(() -> new ResourceNotFoundException("error.parametre.notFound"));
        return parametreMapper.toDto(entity);
    }

    @Override
    public Page<ParametreAdminDto> findAll(Pageable pageable) {
        return parametreRepository.findAll(pageable).map(parametreMapper::toDto);
    }

    @Override
    public Page<ParametreAdminDto> findByCategorie(String categorie, Pageable pageable) {
        return parametreRepository.findByCategorie(categorie, pageable)
                .map(parametreMapper::toDto);
    }
}

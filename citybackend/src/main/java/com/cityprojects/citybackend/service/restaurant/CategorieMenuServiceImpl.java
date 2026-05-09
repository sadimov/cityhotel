package com.cityprojects.citybackend.service.restaurant;

import com.cityprojects.citybackend.common.tenant.RequireTenant;
import com.cityprojects.citybackend.dto.restaurant.CategorieMenuCreateDto;
import com.cityprojects.citybackend.dto.restaurant.CategorieMenuDto;
import com.cityprojects.citybackend.entity.restaurant.CategorieMenu;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.exception.ResourceNotFoundException;
import com.cityprojects.citybackend.mapper.restaurant.CategorieMenuMapper;
import com.cityprojects.citybackend.repository.restaurant.ArticleMenuRepository;
import com.cityprojects.citybackend.repository.restaurant.CategorieMenuRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Implementation de {@link CategorieMenuService}.
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
public class CategorieMenuServiceImpl implements CategorieMenuService {

    private static final Logger logger = LoggerFactory.getLogger(CategorieMenuServiceImpl.class);

    private final CategorieMenuRepository categorieRepository;
    private final ArticleMenuRepository articleRepository;
    private final CategorieMenuMapper mapper;

    public CategorieMenuServiceImpl(CategorieMenuRepository categorieRepository,
                                    ArticleMenuRepository articleRepository,
                                    CategorieMenuMapper mapper) {
        this.categorieRepository = categorieRepository;
        this.articleRepository = articleRepository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public CategorieMenuDto create(CategorieMenuCreateDto dto) {
        logger.info("Creation categorie menu : nom={}", dto.nom());

        CategorieMenu entity = mapper.toEntity(dto);
        if (entity.getOrdre() == null) {
            entity.setOrdre(0);
        }
        entity.setActif(Boolean.TRUE);
        // PAS de setHotelId : Hibernate s'en charge.
        return mapper.toDto(categorieRepository.save(entity));
    }

    @Override
    @Transactional
    public CategorieMenuDto update(Long categorieId, CategorieMenuCreateDto dto) {
        logger.info("Modification categorie menu id={}", categorieId);
        CategorieMenu entity = categorieRepository.findById(categorieId)
                .orElseThrow(() -> new ResourceNotFoundException("error.categorieMenu.notFound"));

        entity.setNom(dto.nom());
        entity.setDescription(dto.description());
        entity.setIconeUrl(dto.iconeUrl());
        if (dto.ordre() != null) {
            entity.setOrdre(dto.ordre());
        }
        return mapper.toDto(categorieRepository.save(entity));
    }

    @Override
    public CategorieMenuDto findById(Long categorieId) {
        CategorieMenu entity = categorieRepository.findById(categorieId)
                .orElseThrow(() -> new ResourceNotFoundException("error.categorieMenu.notFound"));
        return mapper.toDto(entity);
    }

    @Override
    public List<CategorieMenuDto> findAllActive() {
        return categorieRepository.findByActifTrueOrderByOrdreAscNomAsc()
                .stream().map(mapper::toDto).toList();
    }

    @Override
    public Page<CategorieMenuDto> findAll(Boolean actif, Pageable pageable) {
        Page<CategorieMenu> page = (actif != null)
                ? categorieRepository.findByActif(actif, pageable)
                : categorieRepository.findAll(pageable);
        return page.map(mapper::toDto);
    }

    @Override
    @Transactional
    public void deactivate(Long categorieId) {
        logger.info("Desactivation categorie menu id={}", categorieId);
        CategorieMenu entity = categorieRepository.findById(categorieId)
                .orElseThrow(() -> new ResourceNotFoundException("error.categorieMenu.notFound"));

        // Refuser la desactivation si des articles actifs y sont rattaches
        long actifs = articleRepository.countByCategorieIdAndActifTrue(categorieId);
        if (actifs > 0) {
            throw new BusinessException("error.categorieMenu.hasActiveArticles");
        }
        entity.setActif(Boolean.FALSE);
        categorieRepository.save(entity);
    }

    @Override
    @Transactional
    public void reactivate(Long categorieId) {
        logger.info("Reactivation categorie menu id={}", categorieId);
        CategorieMenu entity = categorieRepository.findById(categorieId)
                .orElseThrow(() -> new ResourceNotFoundException("error.categorieMenu.notFound"));
        entity.setActif(Boolean.TRUE);
        categorieRepository.save(entity);
    }
}

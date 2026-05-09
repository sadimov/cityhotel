package com.cityprojects.citybackend.service.inventory;

import com.cityprojects.citybackend.common.tenant.RequireTenant;
import com.cityprojects.citybackend.dto.inventory.CategorieProduitCreateDto;
import com.cityprojects.citybackend.dto.inventory.CategorieProduitDto;
import com.cityprojects.citybackend.entity.inventory.CategorieProduit;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.exception.ResourceNotFoundException;
import com.cityprojects.citybackend.mapper.inventory.CategorieProduitMapper;
import com.cityprojects.citybackend.repository.inventory.CategorieProduitRepository;
import com.cityprojects.citybackend.repository.inventory.ProduitRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Implementation de {@link CategorieProduitService}.
 */
@Service
@RequireTenant
@Transactional(readOnly = true)
public class CategorieProduitServiceImpl implements CategorieProduitService {

    private static final Logger logger = LoggerFactory.getLogger(CategorieProduitServiceImpl.class);

    private final CategorieProduitRepository categorieRepository;
    private final ProduitRepository produitRepository;
    private final CategorieProduitMapper mapper;

    public CategorieProduitServiceImpl(CategorieProduitRepository categorieRepository,
                                       ProduitRepository produitRepository,
                                       CategorieProduitMapper mapper) {
        this.categorieRepository = categorieRepository;
        this.produitRepository = produitRepository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public CategorieProduitDto create(CategorieProduitCreateDto dto) {
        logger.info("Creation categorie : code={}, nom={}", dto.codeCategorie(), dto.nomCategorie());
        if (categorieRepository.existsByCodeCategorie(dto.codeCategorie())) {
            throw new BusinessException("error.categorieProduit.code.alreadyExists");
        }
        CategorieProduit entity = mapper.toEntity(dto);
        entity.setActif(Boolean.TRUE);
        return mapper.toDto(categorieRepository.save(entity));
    }

    @Override
    @Transactional
    public CategorieProduitDto update(Long categorieId, CategorieProduitCreateDto dto) {
        logger.info("Modification categorie id={}", categorieId);
        CategorieProduit entity = categorieRepository.findById(categorieId)
                .orElseThrow(() -> new ResourceNotFoundException("error.categorieProduit.notFound"));

        // Le code n'est pas modifiable apres creation (cle metier des integrations).
        entity.setNomCategorie(dto.nomCategorie());
        entity.setDescription(dto.description());
        return mapper.toDto(categorieRepository.save(entity));
    }

    @Override
    public CategorieProduitDto findById(Long categorieId) {
        CategorieProduit entity = categorieRepository.findById(categorieId)
                .orElseThrow(() -> new ResourceNotFoundException("error.categorieProduit.notFound"));
        return mapper.toDto(entity);
    }

    @Override
    public Page<CategorieProduitDto> search(String recherche, Pageable pageable) {
        return categorieRepository.search(recherche, pageable).map(mapper::toDto);
    }

    @Override
    public List<CategorieProduitDto> findAllActive() {
        return categorieRepository.findByActifTrueOrderByNomCategorieAsc()
                .stream().map(mapper::toDto).toList();
    }

    @Override
    @Transactional
    public void deactivate(Long categorieId) {
        logger.info("Desactivation categorie id={}", categorieId);
        CategorieProduit entity = categorieRepository.findById(categorieId)
                .orElseThrow(() -> new ResourceNotFoundException("error.categorieProduit.notFound"));

        // Refuser la desactivation si des produits actifs y sont rattaches
        long actifs = produitRepository.countByCategorieIdAndActifTrue(categorieId);
        if (actifs > 0) {
            throw new BusinessException("error.categorieProduit.hasActiveProduits");
        }
        entity.setActif(Boolean.FALSE);
        categorieRepository.save(entity);
    }
}

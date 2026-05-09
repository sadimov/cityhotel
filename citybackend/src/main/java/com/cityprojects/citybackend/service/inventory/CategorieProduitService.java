package com.cityprojects.citybackend.service.inventory;

import com.cityprojects.citybackend.dto.inventory.CategorieProduitCreateDto;
import com.cityprojects.citybackend.dto.inventory.CategorieProduitDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Service de gestion des categories de produits.
 */
public interface CategorieProduitService {

    CategorieProduitDto create(CategorieProduitCreateDto dto);

    CategorieProduitDto update(Long categorieId, CategorieProduitCreateDto dto);

    CategorieProduitDto findById(Long categorieId);

    Page<CategorieProduitDto> search(String recherche, Pageable pageable);

    List<CategorieProduitDto> findAllActive();

    void deactivate(Long categorieId);
}

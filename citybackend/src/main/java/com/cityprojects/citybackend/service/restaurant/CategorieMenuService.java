package com.cityprojects.citybackend.service.restaurant;

import com.cityprojects.citybackend.dto.restaurant.CategorieMenuCreateDto;
import com.cityprojects.citybackend.dto.restaurant.CategorieMenuDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Service de gestion du catalogue des categories de menu (par hotel).
 *
 * <p>Toutes les methodes operent dans le tenant courant ; aucun parametre
 * {@code hotelId}.</p>
 */
public interface CategorieMenuService {

    CategorieMenuDto create(CategorieMenuCreateDto dto);

    CategorieMenuDto update(Long categorieId, CategorieMenuCreateDto dto);

    CategorieMenuDto findById(Long categorieId);

    /** Liste des categories actives, triees par {@code ordre} ASC puis {@code nom}. */
    List<CategorieMenuDto> findAllActive();

    Page<CategorieMenuDto> findAll(Boolean actif, Pageable pageable);

    void deactivate(Long categorieId);

    void reactivate(Long categorieId);
}

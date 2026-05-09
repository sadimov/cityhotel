package com.cityprojects.citybackend.service.hebergement;

import com.cityprojects.citybackend.dto.hebergement.TypeChambreCreateDto;
import com.cityprojects.citybackend.dto.hebergement.TypeChambreDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Service de gestion du catalogue des types de chambres (par hotel).
 *
 * <p>Toutes les methodes operent dans le tenant courant ; aucun parametre
 * {@code hotelId}.</p>
 */
public interface TypeChambreService {

    TypeChambreDto create(TypeChambreCreateDto dto);

    TypeChambreDto update(Long typeId, TypeChambreCreateDto dto);

    TypeChambreDto findById(Long typeId);

    List<TypeChambreDto> findAllActive();

    Page<TypeChambreDto> findAll(Boolean actif, Pageable pageable);

    void deactivate(Long typeId);

    void reactivate(Long typeId);
}

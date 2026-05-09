package com.cityprojects.citybackend.service.inventory;

import com.cityprojects.citybackend.dto.inventory.FournisseurCreateDto;
import com.cityprojects.citybackend.dto.inventory.FournisseurDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Service de gestion des fournisseurs.
 *
 * <p>Toutes les operations sont scoped au tenant courant via {@link com.cityprojects.citybackend.common.tenant.TenantContext}
 * (filtrage Hibernate {@code @TenantId}).</p>
 */
public interface FournisseurService {

    FournisseurDto create(FournisseurCreateDto dto);

    FournisseurDto update(Long fournisseurId, FournisseurCreateDto dto);

    FournisseurDto findById(Long fournisseurId);

    Page<FournisseurDto> search(String recherche, Pageable pageable);

    List<FournisseurDto> findAllActive();

    void deactivate(Long fournisseurId);
}

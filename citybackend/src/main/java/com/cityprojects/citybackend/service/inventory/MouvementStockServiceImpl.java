package com.cityprojects.citybackend.service.inventory;

import com.cityprojects.citybackend.common.tenant.RequireTenant;
import com.cityprojects.citybackend.dto.inventory.MouvementStockDto;
import com.cityprojects.citybackend.mapper.inventory.MouvementStockMapper;
import com.cityprojects.citybackend.repository.inventory.MouvementStockRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation de {@link MouvementStockService}.
 */
@Service
@RequireTenant
@Transactional(readOnly = true)
public class MouvementStockServiceImpl implements MouvementStockService {

    private final MouvementStockRepository mouvementRepository;
    private final MouvementStockMapper mapper;

    public MouvementStockServiceImpl(MouvementStockRepository mouvementRepository,
                                     MouvementStockMapper mapper) {
        this.mouvementRepository = mouvementRepository;
        this.mapper = mapper;
    }

    @Override
    public Page<MouvementStockDto> findByProduit(Long produitId, Pageable pageable) {
        return mouvementRepository.findByProduitIdOrderByCreatedAtDesc(produitId, pageable)
                .map(mapper::toDto);
    }

    @Override
    public Page<MouvementStockDto> findAll(Pageable pageable) {
        return mouvementRepository.findAll(pageable).map(mapper::toDto);
    }
}

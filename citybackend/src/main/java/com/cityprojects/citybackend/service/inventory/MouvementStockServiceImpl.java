package com.cityprojects.citybackend.service.inventory;

import com.cityprojects.citybackend.common.tenant.RequireTenant;
import com.cityprojects.citybackend.dto.inventory.MouvementStockDto;
import com.cityprojects.citybackend.entity.inventory.MouvementStock;
import com.cityprojects.citybackend.entity.inventory.Produit;
import com.cityprojects.citybackend.mapper.inventory.MouvementStockMapper;
import com.cityprojects.citybackend.repository.inventory.MouvementStockRepository;
import com.cityprojects.citybackend.repository.inventory.ProduitRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Implementation de {@link MouvementStockService}.
 */
@Service
@RequireTenant
@Transactional(readOnly = true)
public class MouvementStockServiceImpl implements MouvementStockService {

    private final MouvementStockRepository mouvementRepository;
    private final ProduitRepository produitRepository;
    private final MouvementStockMapper mapper;

    public MouvementStockServiceImpl(MouvementStockRepository mouvementRepository,
                                     ProduitRepository produitRepository,
                                     MouvementStockMapper mapper) {
        this.mouvementRepository = mouvementRepository;
        this.produitRepository = produitRepository;
        this.mapper = mapper;
    }

    @Override
    public Page<MouvementStockDto> findByProduit(Long produitId, Pageable pageable) {
        return enrichPage(mouvementRepository
                .findByProduitIdOrderByCreatedAtDesc(produitId, pageable));
    }

    @Override
    public Page<MouvementStockDto> findAll(Pageable pageable) {
        return enrichPage(mouvementRepository.findAll(pageable));
    }

    /**
     * Enrichit une page de mouvements avec les noms produits (batch lookup
     * anti-N+1 via {@code findAllById(Set<Long>)}).
     */
    private Page<MouvementStockDto> enrichPage(Page<MouvementStock> page) {
        List<MouvementStock> content = page.getContent();
        if (content.isEmpty()) {
            return page.map(mapper::toDto);
        }
        Set<Long> produitIds = content.stream()
                .map(MouvementStock::getProduitId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, Produit> produits = produitRepository.findAllById(produitIds).stream()
                .collect(Collectors.toMap(Produit::getProduitId, p -> p));
        return page.map(m -> {
            MouvementStockDto base = mapper.toDto(m);
            Produit p = produits.get(m.getProduitId());
            return base.withResolvedNames(
                    p != null ? p.getNomProduit() : null,
                    p != null ? p.getCodeProduit() : null);
        });
    }
}

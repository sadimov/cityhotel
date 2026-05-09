package com.cityprojects.citybackend.service.inventory;

import com.cityprojects.citybackend.common.tenant.RequireTenant;
import com.cityprojects.citybackend.dto.inventory.BonSortieCreateDto;
import com.cityprojects.citybackend.dto.inventory.BonSortieDto;
import com.cityprojects.citybackend.dto.inventory.LigneBonSortieCreateDto;
import com.cityprojects.citybackend.dto.inventory.LigneBonSortieDto;
import com.cityprojects.citybackend.entity.inventory.BonSortie;
import com.cityprojects.citybackend.entity.inventory.LigneBonSortie;
import com.cityprojects.citybackend.entity.inventory.MouvementStock;
import com.cityprojects.citybackend.entity.inventory.Produit;
import com.cityprojects.citybackend.entity.inventory.StatutBonSortie;
import com.cityprojects.citybackend.entity.inventory.TypeMouvementStock;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.exception.ResourceNotFoundException;
import com.cityprojects.citybackend.mapper.inventory.BonSortieMapper;
import com.cityprojects.citybackend.repository.inventory.BonSortieRepository;
import com.cityprojects.citybackend.repository.inventory.LigneBonSortieRepository;
import com.cityprojects.citybackend.repository.inventory.MouvementStockRepository;
import com.cityprojects.citybackend.repository.inventory.ProduitRepository;
import com.cityprojects.citybackend.security.UserPrincipal;
import com.cityprojects.citybackend.service.finance.NumerotationService;
import com.cityprojects.citybackend.service.finance.TypeNumerotation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Implementation de {@link BonSortieService}.
 */
@Service
@RequireTenant
@Transactional(readOnly = true)
public class BonSortieServiceImpl implements BonSortieService {

    private static final Logger logger = LoggerFactory.getLogger(BonSortieServiceImpl.class);

    private final BonSortieRepository bsRepository;
    private final LigneBonSortieRepository ligneRepository;
    private final ProduitRepository produitRepository;
    private final MouvementStockRepository mouvementRepository;
    private final BonSortieMapper mapper;
    private final NumerotationService numerotationService;

    public BonSortieServiceImpl(BonSortieRepository bsRepository,
                                LigneBonSortieRepository ligneRepository,
                                ProduitRepository produitRepository,
                                MouvementStockRepository mouvementRepository,
                                BonSortieMapper mapper,
                                NumerotationService numerotationService) {
        this.bsRepository = bsRepository;
        this.ligneRepository = ligneRepository;
        this.produitRepository = produitRepository;
        this.mouvementRepository = mouvementRepository;
        this.mapper = mapper;
        this.numerotationService = numerotationService;
    }

    @Override
    @Transactional
    public BonSortieDto create(BonSortieCreateDto dto) {
        logger.info("Creation BS : destination={}, lignes={}", dto.destination(), dto.lignes().size());

        // Validations produits
        for (LigneBonSortieCreateDto l : dto.lignes()) {
            Produit p = produitRepository.findById(l.produitId())
                    .orElseThrow(() -> new ResourceNotFoundException("error.produit.notFound"));
            if (!Boolean.TRUE.equals(p.getActif())) {
                throw new BusinessException("error.bonSortie.produit.inactive");
            }
        }

        BonSortie bs = new BonSortie();
        bs.setDestination(dto.destination());
        bs.setStatut(StatutBonSortie.BROUILLON);
        bs.setCommentaires(dto.commentaires());
        bs.setUserId(currentUserId());
        bs.setNumeroBs(numerotationService.next(TypeNumerotation.BS));
        BonSortie saved = bsRepository.save(bs);

        for (LigneBonSortieCreateDto ld : dto.lignes()) {
            LigneBonSortie ligne = new LigneBonSortie();
            ligne.setBonSortieId(saved.getBonSortieId());
            ligne.setProduitId(ld.produitId());
            ligne.setQuantiteDemandee(ld.quantiteDemandee());
            ligne.setQuantiteServie(0);
            ligne.setCommentaires(ld.commentaires());
            ligneRepository.save(ligne);
        }

        logger.info("BS cree : id={}, numero={}", saved.getBonSortieId(), saved.getNumeroBs());
        return toDtoWithLignes(saved);
    }

    @Override
    public BonSortieDto findById(Long bonSortieId) {
        BonSortie bs = bsRepository.findById(bonSortieId)
                .orElseThrow(() -> new ResourceNotFoundException("error.bonSortie.notFound"));
        return toDtoWithLignes(bs);
    }

    @Override
    public Page<BonSortieDto> findByStatut(StatutBonSortie statut, Pageable pageable) {
        Page<BonSortie> page = (statut != null)
                ? bsRepository.findByStatutOrderByDateSortieDesc(statut, pageable)
                : bsRepository.findAll(pageable);
        return page.map(this::toDtoWithLignes);
    }

    @Override
    @Transactional
    public BonSortieDto valider(Long bonSortieId) {
        BonSortie bs = bsRepository.findById(bonSortieId)
                .orElseThrow(() -> new ResourceNotFoundException("error.bonSortie.notFound"));
        if (bs.getStatut() != StatutBonSortie.BROUILLON) {
            throw new BusinessException("error.bonSortie.validation.statutInvalide");
        }
        // Verifier que les stocks sont disponibles pour toutes les lignes
        for (LigneBonSortie ligne : ligneRepository.findByBonSortieIdOrderByLigneIdAsc(bonSortieId)) {
            Produit p = produitRepository.findById(ligne.getProduitId())
                    .orElseThrow(() -> new ResourceNotFoundException("error.produit.notFound"));
            int stock = p.getStockActuel() != null ? p.getStockActuel() : 0;
            if (stock < ligne.getQuantiteDemandee()) {
                throw new BusinessException("error.bonSortie.stockInsuffisant");
            }
        }
        bs.setStatut(StatutBonSortie.VALIDE);
        bsRepository.save(bs);
        return toDtoWithLignes(bs);
    }

    @Override
    @Transactional
    public BonSortieDto livrer(Long bonSortieId) {
        logger.info("Livraison BS id={}", bonSortieId);
        BonSortie bs = bsRepository.findById(bonSortieId)
                .orElseThrow(() -> new ResourceNotFoundException("error.bonSortie.notFound"));
        if (!StatutBonSortie.peutEtreLivre(bs.getStatut())) {
            throw new BusinessException("error.bonSortie.livraison.statutInvalide");
        }

        Long userId = currentUserId();

        for (LigneBonSortie ligne : ligneRepository.findByBonSortieIdOrderByLigneIdAsc(bonSortieId)) {
            Produit produit = produitRepository.findById(ligne.getProduitId())
                    .orElseThrow(() -> new ResourceNotFoundException("error.produit.notFound"));
            int stockAvant = produit.getStockActuel() != null ? produit.getStockActuel() : 0;
            int aSortir = ligne.getQuantiteDemandee();
            int stockApres = stockAvant - aSortir;
            if (stockApres < 0) {
                // Garde defensive (la validation a deja verifie, mais le stock peut
                // avoir baisse entre temps via un autre BS).
                throw new BusinessException("error.bonSortie.stockInsuffisant");
            }
            produit.setStockActuel(stockApres);
            produitRepository.save(produit);

            ligne.setQuantiteServie(aSortir);
            ligneRepository.save(ligne);

            MouvementStock mouvement = new MouvementStock();
            mouvement.setProduitId(produit.getProduitId());
            mouvement.setTypeMouvement(TypeMouvementStock.SORTIE);
            mouvement.setQuantite(aSortir);
            mouvement.setPrixUnitaire(produit.getPrixUnitaire());
            mouvement.setStockAvant(stockAvant);
            mouvement.setStockApres(stockApres);
            mouvement.setReferenceDocument(bs.getNumeroBs());
            mouvement.setUserId(userId);
            mouvementRepository.save(mouvement);
        }

        bs.setStatut(StatutBonSortie.LIVRE);
        bsRepository.save(bs);
        return toDtoWithLignes(bs);
    }

    @Override
    @Transactional
    public BonSortieDto annuler(Long bonSortieId) {
        BonSortie bs = bsRepository.findById(bonSortieId)
                .orElseThrow(() -> new ResourceNotFoundException("error.bonSortie.notFound"));
        if (!StatutBonSortie.peutEtreAnnule(bs.getStatut())) {
            throw new BusinessException("error.bonSortie.annulation.statutInvalide");
        }
        bs.setStatut(StatutBonSortie.ANNULE);
        bsRepository.save(bs);
        return toDtoWithLignes(bs);
    }

    private BonSortieDto toDtoWithLignes(BonSortie bs) {
        BonSortieDto base = mapper.toDto(bs);
        List<LigneBonSortieDto> lignes = ligneRepository
                .findByBonSortieIdOrderByLigneIdAsc(bs.getBonSortieId())
                .stream().map(mapper::toLigneDto).toList();
        return mapper.withLignes(base, lignes);
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            return principal.getUserId();
        }
        throw new BusinessException("error.user.unknown");
    }
}

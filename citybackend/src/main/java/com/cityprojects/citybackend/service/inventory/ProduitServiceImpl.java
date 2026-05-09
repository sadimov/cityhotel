package com.cityprojects.citybackend.service.inventory;

import com.cityprojects.citybackend.common.tenant.RequireTenant;
import com.cityprojects.citybackend.dto.inventory.AjustementStockDto;
import com.cityprojects.citybackend.dto.inventory.ProduitCreateDto;
import com.cityprojects.citybackend.dto.inventory.ProduitDto;
import com.cityprojects.citybackend.entity.inventory.CategorieProduit;
import com.cityprojects.citybackend.entity.inventory.Fournisseur;
import com.cityprojects.citybackend.entity.inventory.MouvementStock;
import com.cityprojects.citybackend.entity.inventory.Produit;
import com.cityprojects.citybackend.entity.inventory.TypeMouvementStock;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.exception.ResourceNotFoundException;
import com.cityprojects.citybackend.mapper.inventory.ProduitMapper;
import com.cityprojects.citybackend.repository.inventory.CategorieProduitRepository;
import com.cityprojects.citybackend.repository.inventory.FournisseurRepository;
import com.cityprojects.citybackend.repository.inventory.MouvementStockRepository;
import com.cityprojects.citybackend.repository.inventory.ProduitRepository;
import com.cityprojects.citybackend.security.UserPrincipal;
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
 * Implementation de {@link ProduitService}.
 *
 * <p>Conventions appliquees (cf. citybackend/CLAUDE.md §3.3) :
 * <ul>
 *   <li>{@code @RequireTenant} au niveau classe.</li>
 *   <li>{@code @Transactional(readOnly = true)} a la classe, override en ecriture.</li>
 *   <li>Aucun {@code setHotelId} : Hibernate populate via le resolver.</li>
 *   <li>{@code stockActuel} initial = 0 (ne dependra que des flux BC/BS/AJUSTEMENT).</li>
 *   <li>Mouvement de stock cree systematiquement avec le {@code stockActuel} dans
 *       la meme transaction (double ecriture atomique).</li>
 * </ul>
 */
@Service
@RequireTenant
@Transactional(readOnly = true)
public class ProduitServiceImpl implements ProduitService {

    private static final Logger logger = LoggerFactory.getLogger(ProduitServiceImpl.class);

    private final ProduitRepository produitRepository;
    private final CategorieProduitRepository categorieRepository;
    private final FournisseurRepository fournisseurRepository;
    private final MouvementStockRepository mouvementRepository;
    private final ProduitMapper produitMapper;

    public ProduitServiceImpl(ProduitRepository produitRepository,
                              CategorieProduitRepository categorieRepository,
                              FournisseurRepository fournisseurRepository,
                              MouvementStockRepository mouvementRepository,
                              ProduitMapper produitMapper) {
        this.produitRepository = produitRepository;
        this.categorieRepository = categorieRepository;
        this.fournisseurRepository = fournisseurRepository;
        this.mouvementRepository = mouvementRepository;
        this.produitMapper = produitMapper;
    }

    @Override
    @Transactional
    public ProduitDto create(ProduitCreateDto dto) {
        logger.info("Creation produit : code={}, nom={}", dto.codeProduit(), dto.nomProduit());

        if (produitRepository.existsByCodeProduit(dto.codeProduit())) {
            throw new BusinessException("error.produit.code.alreadyExists");
        }
        // Verification existence categorie (filtre tenant Hibernate)
        CategorieProduit categorie = categorieRepository.findById(dto.categorieId())
                .orElseThrow(() -> new ResourceNotFoundException("error.categorieProduit.notFound"));
        if (!Boolean.TRUE.equals(categorie.getActif())) {
            throw new BusinessException("error.produit.categorie.inactive");
        }
        if (dto.fournisseurPrincipalId() != null) {
            Fournisseur fournisseur = fournisseurRepository.findById(dto.fournisseurPrincipalId())
                    .orElseThrow(() -> new ResourceNotFoundException("error.fournisseur.notFound"));
            if (!Boolean.TRUE.equals(fournisseur.getActif())) {
                throw new BusinessException("error.produit.fournisseur.inactive");
            }
        }

        Produit entity = produitMapper.toEntity(dto);
        entity.setActif(Boolean.TRUE);
        entity.setStockActuel(0);
        // Forcer la valeur par defaut quand le DTO ne fournit pas le flag
        // (Boolean wrapper laisse null par MapStruct, mais la colonne est NOT NULL).
        if (entity.getEstFacturable() == null) {
            entity.setEstFacturable(Boolean.FALSE);
        }
        // Idem pour les seuils (peuvent etre null en entree, defaults a 0)
        if (entity.getSeuilAlerte() == null) {
            entity.setSeuilAlerte(0);
        }
        if (entity.getSeuilCritique() == null) {
            entity.setSeuilCritique(0);
        }
        if (entity.getPrixUnitaire() == null) {
            entity.setPrixUnitaire(java.math.BigDecimal.ZERO);
        }
        Produit saved = produitRepository.save(entity);
        logger.info("Produit cree : id={}, code={}", saved.getProduitId(), saved.getCodeProduit());
        return produitMapper.toDto(saved);
    }

    @Override
    @Transactional
    public ProduitDto update(Long produitId, ProduitCreateDto dto) {
        logger.info("Modification produit id={}", produitId);
        Produit entity = produitRepository.findById(produitId)
                .orElseThrow(() -> new ResourceNotFoundException("error.produit.notFound"));

        // codeProduit immuable apres creation (cle metier).
        entity.setNomProduit(dto.nomProduit());
        entity.setDescription(dto.description());
        if (dto.categorieId() != null && !dto.categorieId().equals(entity.getCategorieId())) {
            CategorieProduit categorie = categorieRepository.findById(dto.categorieId())
                    .orElseThrow(() -> new ResourceNotFoundException("error.categorieProduit.notFound"));
            if (!Boolean.TRUE.equals(categorie.getActif())) {
                throw new BusinessException("error.produit.categorie.inactive");
            }
            entity.setCategorieId(dto.categorieId());
        }
        entity.setUniteMesure(dto.uniteMesure());
        if (dto.prixUnitaire() != null) {
            entity.setPrixUnitaire(dto.prixUnitaire());
        }
        if (dto.seuilAlerte() != null) {
            entity.setSeuilAlerte(dto.seuilAlerte());
        }
        if (dto.seuilCritique() != null) {
            entity.setSeuilCritique(dto.seuilCritique());
        }
        entity.setFournisseurPrincipalId(dto.fournisseurPrincipalId());
        if (dto.estFacturable() != null) {
            entity.setEstFacturable(dto.estFacturable());
        }

        return produitMapper.toDto(produitRepository.save(entity));
    }

    @Override
    public ProduitDto findById(Long produitId) {
        Produit entity = produitRepository.findById(produitId)
                .orElseThrow(() -> new ResourceNotFoundException("error.produit.notFound"));
        return produitMapper.toDto(entity);
    }

    @Override
    public Page<ProduitDto> search(String recherche, Long categorieId, Pageable pageable) {
        return produitRepository.search(recherche, categorieId, pageable).map(produitMapper::toDto);
    }

    @Override
    public List<ProduitDto> findAllActive() {
        return produitRepository.findByActifTrueOrderByNomProduitAsc()
                .stream().map(produitMapper::toDto).toList();
    }

    @Override
    public List<ProduitDto> findEnAlerte() {
        return produitRepository.findEnAlerte().stream().map(produitMapper::toDto).toList();
    }

    @Override
    public List<ProduitDto> findEnStockCritique() {
        return produitRepository.findEnStockCritique().stream().map(produitMapper::toDto).toList();
    }

    @Override
    @Transactional
    public ProduitDto ajusterStock(Long produitId, AjustementStockDto dto) {
        logger.info("Ajustement stock produit id={}, type={}, quantite={}",
                produitId, dto.typeMouvement(), dto.quantite());

        // Limite explicite : seuls AJUSTEMENT et PERTE sont des ajustements directs
        if (dto.typeMouvement() != TypeMouvementStock.AJUSTEMENT
                && dto.typeMouvement() != TypeMouvementStock.PERTE) {
            throw new BusinessException("error.ajustement.type.notAllowed");
        }

        Produit produit = produitRepository.findById(produitId)
                .orElseThrow(() -> new ResourceNotFoundException("error.produit.notFound"));

        Integer stockAvant = produit.getStockActuel() != null ? produit.getStockActuel() : 0;
        // PERTE : on retire toujours (quantite traitee comme positive).
        // AJUSTEMENT : signe arbitraire (positif ou negatif).
        Integer delta = dto.typeMouvement() == TypeMouvementStock.PERTE
                ? -Math.abs(dto.quantite()) : dto.quantite();
        Integer stockApres = stockAvant + delta;
        if (stockApres < 0) {
            throw new BusinessException("error.ajustement.stockNegatif");
        }
        produit.setStockActuel(stockApres);
        produitRepository.save(produit);

        // Audit trail
        MouvementStock mouvement = new MouvementStock();
        mouvement.setProduitId(produitId);
        mouvement.setTypeMouvement(dto.typeMouvement());
        mouvement.setQuantite(Math.abs(dto.quantite()));
        mouvement.setPrixUnitaire(produit.getPrixUnitaire());
        mouvement.setStockAvant(stockAvant);
        mouvement.setStockApres(stockApres);
        mouvement.setCommentaire(dto.commentaire());
        mouvement.setUserId(currentUserId());
        mouvementRepository.save(mouvement);

        return produitMapper.toDto(produit);
    }

    @Override
    @Transactional
    public void deactivate(Long produitId) {
        logger.info("Desactivation produit id={}", produitId);
        Produit entity = produitRepository.findById(produitId)
                .orElseThrow(() -> new ResourceNotFoundException("error.produit.notFound"));
        entity.setActif(Boolean.FALSE);
        produitRepository.save(entity);
    }

    /**
     * Recupere l'identifiant utilisateur courant depuis le SecurityContext.
     * Pattern Tour 11 (cf. ReservationServiceImpl).
     */
    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            return principal.getUserId();
        }
        throw new BusinessException("error.user.unknown");
    }
}

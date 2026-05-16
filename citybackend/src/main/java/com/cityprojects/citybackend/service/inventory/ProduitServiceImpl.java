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
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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
    private final NumerotationService numerotationService;

    public ProduitServiceImpl(ProduitRepository produitRepository,
                              CategorieProduitRepository categorieRepository,
                              FournisseurRepository fournisseurRepository,
                              MouvementStockRepository mouvementRepository,
                              ProduitMapper produitMapper,
                              NumerotationService numerotationService) {
        this.produitRepository = produitRepository;
        this.categorieRepository = categorieRepository;
        this.fournisseurRepository = fournisseurRepository;
        this.mouvementRepository = mouvementRepository;
        this.produitMapper = produitMapper;
        this.numerotationService = numerotationService;
    }

    @Override
    @Transactional
    public ProduitDto create(ProduitCreateDto dto) {
        // Code produit : auto-genere via NumerotationService.PROD si le DTO
        // ne fournit pas de valeur (cas par defaut : le front laisse le champ
        // vide en mode creation). Si l'appelant fournit un code, on l'utilise
        // tel quel apres normalisation (trim + uppercase) et verification d'unicite.
        String code = (dto.codeProduit() != null) ? dto.codeProduit().trim() : "";
        if (code.isEmpty()) {
            code = numerotationService.next(TypeNumerotation.PROD);
        } else {
            code = code.toUpperCase();
        }
        logger.info("Creation produit : code={}, nom={}", code, dto.nomProduit());

        if (produitRepository.existsByCodeProduit(code)) {
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
        // Le mapper a recopie codeProduit depuis le DTO ; on ecrase avec la
        // valeur normalisee (auto-generee ou trim+upper).
        entity.setCodeProduit(code);
        entity.setActif(Boolean.TRUE);
        // Stock initial : on prend la valeur du DTO si fournie et >= 0,
        // sinon 0. Un MouvementStock AJUSTEMENT est cree apres save() pour
        // la tracabilite si le stock initial est > 0.
        int stockInitial = (dto.stockActuel() != null && dto.stockActuel() >= 0)
                ? dto.stockActuel()
                : 0;
        entity.setStockActuel(stockInitial);
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
        logger.info("Produit cree : id={}, code={}, stockInitial={}",
                saved.getProduitId(), saved.getCodeProduit(), stockInitial);

        // Audit trail : si stock initial > 0, on enregistre un mouvement
        // AJUSTEMENT pour conserver la tracabilite (cf. doctrine inventory :
        // tout changement de stockActuel passe par un MouvementStock).
        if (stockInitial > 0) {
            MouvementStock mouvement = new MouvementStock();
            mouvement.setProduitId(saved.getProduitId());
            mouvement.setTypeMouvement(TypeMouvementStock.AJUSTEMENT);
            mouvement.setQuantite(stockInitial);
            mouvement.setPrixUnitaire(saved.getPrixUnitaire());
            mouvement.setStockAvant(0);
            mouvement.setStockApres(stockInitial);
            mouvement.setCommentaire("Stock initial a la creation du produit");
            mouvement.setUserId(currentUserId());
            mouvementRepository.save(mouvement);
        }

        return toDtoWithNames(saved);
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

        // Stock : si le DTO fournit une valeur differente du stock courant,
        // on l'applique ET on cree un MouvementStock AJUSTEMENT pour
        // tracabilite (cf. doctrine inventory : tout changement de
        // stockActuel reste reflete par un mouvement d'audit).
        Integer stockAvantUpdate = null;
        Integer stockApresUpdate = null;
        if (dto.stockActuel() != null
                && !dto.stockActuel().equals(entity.getStockActuel())) {
            stockAvantUpdate = entity.getStockActuel() != null ? entity.getStockActuel() : 0;
            stockApresUpdate = dto.stockActuel();
            entity.setStockActuel(stockApresUpdate);
        }

        Produit saved = produitRepository.save(entity);

        if (stockAvantUpdate != null) {
            MouvementStock mouvement = new MouvementStock();
            mouvement.setProduitId(saved.getProduitId());
            mouvement.setTypeMouvement(TypeMouvementStock.AJUSTEMENT);
            mouvement.setQuantite(Math.abs(stockApresUpdate - stockAvantUpdate));
            mouvement.setPrixUnitaire(saved.getPrixUnitaire());
            mouvement.setStockAvant(stockAvantUpdate);
            mouvement.setStockApres(stockApresUpdate);
            mouvement.setCommentaire("Ajustement stock via modification du produit");
            mouvement.setUserId(currentUserId());
            mouvementRepository.save(mouvement);
            logger.info("Mouvement stock AJUSTEMENT cree : produitId={}, avant={}, apres={}",
                    saved.getProduitId(), stockAvantUpdate, stockApresUpdate);
        }

        return toDtoWithNames(saved);
    }

    @Override
    public ProduitDto findById(Long produitId) {
        Produit entity = produitRepository.findById(produitId)
                .orElseThrow(() -> new ResourceNotFoundException("error.produit.notFound"));
        return toDtoWithNames(entity);
    }

    @Override
    public Page<ProduitDto> search(String recherche, Long categorieId, Pageable pageable) {
        Page<Produit> page = produitRepository.search(recherche, categorieId, pageable);
        List<ProduitDto> dtos = toDtosWithNames(page.getContent());
        return new org.springframework.data.domain.PageImpl<>(dtos, page.getPageable(), page.getTotalElements());
    }

    @Override
    public List<ProduitDto> findAllActive() {
        return toDtosWithNames(produitRepository.findByActifTrueOrderByNomProduitAsc());
    }

    @Override
    public List<ProduitDto> findEnAlerte() {
        return toDtosWithNames(produitRepository.findEnAlerte());
    }

    @Override
    public List<ProduitDto> findEnStockCritique() {
        return toDtosWithNames(produitRepository.findEnStockCritique());
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

        return toDtoWithNames(produit);
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

    @Override
    @Transactional
    public void delete(Long produitId) {
        logger.info("Suppression definitive produit id={}", produitId);
        Produit entity = produitRepository.findById(produitId)
                .orElseThrow(() -> new ResourceNotFoundException("error.produit.notFound"));
        // Garde-fou : on ne supprime PAS un produit qui a deja ete utilise
        // (mouvements de stock = audit trail comptable). L'appelant doit
        // passer par deactivate() pour conserver l'historique.
        if (mouvementRepository.existsByProduitId(produitId)) {
            throw new BusinessException("error.produit.delete.hasMouvements");
        }
        produitRepository.delete(entity);
    }

    @Override
    @Transactional
    public void reactivate(Long produitId) {
        logger.info("Reactivation produit id={}", produitId);
        Produit entity = produitRepository.findById(produitId)
                .orElseThrow(() -> new ResourceNotFoundException("error.produit.notFound"));
        entity.setActif(Boolean.TRUE);
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

    /**
     * Construit un {@link ProduitDto} enrichi avec les noms resolus de
     * categorie et de fournisseur principal (lookup unitaire). Pour les
     * listes ou pages, preferer {@link #toDtosWithNames(List)} qui batche
     * les lookups pour eviter le N+1.
     */
    private ProduitDto toDtoWithNames(Produit entity) {
        ProduitDto base = produitMapper.toDto(entity);
        String nomCat = entity.getCategorieId() != null
                ? categorieRepository.findById(entity.getCategorieId())
                        .map(CategorieProduit::getNomCategorie)
                        .orElse(null)
                : null;
        String nomFour = entity.getFournisseurPrincipalId() != null
                ? fournisseurRepository.findById(entity.getFournisseurPrincipalId())
                        .map(Fournisseur::getNomFournisseur)
                        .orElse(null)
                : null;
        return produitMapper.withResolvedNames(base, nomCat, nomFour);
    }

    /**
     * Version batchee de {@link #toDtoWithNames(Produit)} : un seul lookup
     * groupe par categorie et par fournisseur, indispensable pour les listes
     * et pages (anti-N+1).
     */
    private List<ProduitDto> toDtosWithNames(List<Produit> entities) {
        if (entities.isEmpty()) {
            return List.of();
        }
        Set<Long> categorieIds = entities.stream()
                .map(Produit::getCategorieId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> fournisseurIds = entities.stream()
                .map(Produit::getFournisseurPrincipalId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> nomsCategories = categorieIds.isEmpty()
                ? Map.of()
                : categorieRepository.findAllById(categorieIds).stream()
                        .collect(Collectors.toMap(
                                CategorieProduit::getCategorieId,
                                CategorieProduit::getNomCategorie));
        Map<Long, String> nomsFournisseurs = fournisseurIds.isEmpty()
                ? Map.of()
                : fournisseurRepository.findAllById(fournisseurIds).stream()
                        .collect(Collectors.toMap(
                                Fournisseur::getFournisseurId,
                                Fournisseur::getNomFournisseur));
        return entities.stream().map(e -> {
            ProduitDto base = produitMapper.toDto(e);
            String nomCat = e.getCategorieId() != null
                    ? nomsCategories.get(e.getCategorieId()) : null;
            String nomFour = e.getFournisseurPrincipalId() != null
                    ? nomsFournisseurs.get(e.getFournisseurPrincipalId()) : null;
            return produitMapper.withResolvedNames(base, nomCat, nomFour);
        }).toList();
    }
}

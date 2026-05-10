package com.cityprojects.citybackend.service.restaurant;

import com.cityprojects.citybackend.common.tenant.RequireTenant;
import com.cityprojects.citybackend.dto.restaurant.LigneRecetteDto;
import com.cityprojects.citybackend.dto.restaurant.RecetteArticleCreateDto;
import com.cityprojects.citybackend.dto.restaurant.RecetteArticleDto;
import com.cityprojects.citybackend.entity.restaurant.RecetteArticle;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.exception.ResourceNotFoundException;
import com.cityprojects.citybackend.mapper.restaurant.RecetteArticleMapper;
import com.cityprojects.citybackend.repository.inventory.ProduitRepository;
import com.cityprojects.citybackend.repository.restaurant.ArticleMenuRepository;
import com.cityprojects.citybackend.repository.restaurant.RecetteArticleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Implementation de {@link RecetteArticleService} (Tour 25).
 *
 * <h3>Conventions</h3>
 * <ul>
 *   <li>{@code @RequireTenant} au niveau classe.</li>
 *   <li>{@code @Transactional(readOnly = true)} a la classe, override en ecriture.</li>
 *   <li>Validation FK cross-module : article (tenant courant) + produit (tenant courant)
 *       via {@link ArticleMenuRepository} et {@link ProduitRepository}. Hibernate
 *       filtre par {@code @TenantId} : un id d'un autre hotel renvoie {@code Optional.empty}.</li>
 *   <li>Soft delete uniquement ({@code actif = false}), pas de delete physique.</li>
 * </ul>
 */
@Service
@RequireTenant
@Transactional(readOnly = true)
public class RecetteArticleServiceImpl implements RecetteArticleService {

    private static final Logger logger = LoggerFactory.getLogger(RecetteArticleServiceImpl.class);

    private final RecetteArticleRepository recetteRepository;
    private final ArticleMenuRepository articleRepository;
    private final ProduitRepository produitRepository;
    private final RecetteArticleMapper mapper;

    public RecetteArticleServiceImpl(RecetteArticleRepository recetteRepository,
                                     ArticleMenuRepository articleRepository,
                                     ProduitRepository produitRepository,
                                     RecetteArticleMapper mapper) {
        this.recetteRepository = recetteRepository;
        this.articleRepository = articleRepository;
        this.produitRepository = produitRepository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public RecetteArticleDto create(RecetteArticleCreateDto dto) {
        validateArticleAndProduit(dto.articleId(), dto.produitId());

        if (recetteRepository.existsByArticleIdAndProduitId(dto.articleId(), dto.produitId())) {
            throw new BusinessException("error.recetteArticle.dejaExistant");
        }

        RecetteArticle entity = mapper.toEntity(dto);
        // PAS de setHotelId : Hibernate via @TenantId resolver.
        entity.setActif(Boolean.TRUE);
        RecetteArticle saved = recetteRepository.save(entity);

        logger.info("Recette creee : id={}, articleId={}, produitId={}, qte={}",
                saved.getRecetteId(), saved.getArticleId(), saved.getProduitId(),
                saved.getQuantiteParUnite());
        return mapper.toDto(saved);
    }

    @Override
    @Transactional
    public RecetteArticleDto update(Long recetteId, RecetteArticleCreateDto dto) {
        RecetteArticle entity = recetteRepository.findById(recetteId)
                .orElseThrow(() -> new ResourceNotFoundException("error.recetteArticle.notFound"));

        // articleId / produitId non modifiables : on refuse une tentative de change.
        if (!entity.getArticleId().equals(dto.articleId())
                || !entity.getProduitId().equals(dto.produitId())) {
            throw new BusinessException("error.recetteArticle.cleNonModifiable");
        }

        entity.setQuantiteParUnite(dto.quantiteParUnite());
        entity.setUnite(dto.unite());
        entity.setNote(dto.note());
        RecetteArticle saved = recetteRepository.save(entity);

        logger.info("Recette mise a jour : id={}, qte={}", saved.getRecetteId(),
                saved.getQuantiteParUnite());
        return mapper.toDto(saved);
    }

    @Override
    @Transactional
    public void delete(Long recetteId) {
        RecetteArticle entity = recetteRepository.findById(recetteId)
                .orElseThrow(() -> new ResourceNotFoundException("error.recetteArticle.notFound"));
        entity.setActif(Boolean.FALSE);
        recetteRepository.save(entity);
        logger.info("Recette desactivee (soft delete) : id={}", recetteId);
    }

    @Override
    public RecetteArticleDto findById(Long recetteId) {
        RecetteArticle entity = recetteRepository.findById(recetteId)
                .orElseThrow(() -> new ResourceNotFoundException("error.recetteArticle.notFound"));
        return mapper.toDto(entity);
    }

    @Override
    public List<RecetteArticleDto> findActiveByArticle(Long articleId) {
        return recetteRepository.findByArticleIdAndActifTrue(articleId)
                .stream().map(mapper::toDto).toList();
    }

    @Override
    public List<RecetteArticleDto> findAllByArticle(Long articleId) {
        return recetteRepository.findByArticleId(articleId)
                .stream().map(mapper::toDto).toList();
    }

    @Override
    @Transactional
    public List<RecetteArticleDto> setRecetteForArticle(Long articleId, List<LigneRecetteDto> lignes) {
        // Verifie que l'article existe dans le tenant courant
        articleRepository.findById(articleId)
                .orElseThrow(() -> new ResourceNotFoundException("error.articleMenu.notFound"));

        validateNoDuplicateProduits(lignes);

        // 1) Soft-delete : desactive toutes les lignes existantes
        List<RecetteArticle> existantes = deactivateExistingRecettes(articleId);

        // 2) Cree (ou re-active) les nouvelles lignes fournies
        List<RecetteArticleDto> resultat = new ArrayList<>();
        if (lignes == null) {
            return resultat;
        }
        for (LigneRecetteDto l : lignes) {
            RecetteArticle saved = upsertLigne(articleId, existantes, l);
            resultat.add(mapper.toDto(saved));
        }

        logger.info("Recette d'article {} remplacee : {} lignes actives", articleId, resultat.size());
        return resultat;
    }

    /**
     * F5 : refus des doublons sur produitId dans la liste fournie - on ne
     * peut pas avoir 2 lignes pour le meme produit dans une recette.
     *
     * <p>Tour 40bis (refactor H10).</p>
     */
    private void validateNoDuplicateProduits(List<LigneRecetteDto> lignes) {
        if (lignes == null) {
            return;
        }
        Set<Long> produitIds = new HashSet<>();
        for (LigneRecetteDto l : lignes) {
            if (!produitIds.add(l.produitId())) {
                throw new BusinessException("error.recetteArticle.produit.duplique");
            }
        }
    }

    /**
     * Desactive toutes les recettes existantes pour cet article (soft delete).
     * Retourne la liste des entites avant desactivation pour permettre la
     * reactivation eventuelle a l'etape suivante.
     *
     * <p>Tour 40bis (refactor H10).</p>
     */
    private List<RecetteArticle> deactivateExistingRecettes(Long articleId) {
        List<RecetteArticle> existantes = recetteRepository.findByArticleId(articleId);
        for (RecetteArticle r : existantes) {
            r.setActif(Boolean.FALSE);
            recetteRepository.save(r);
        }
        return existantes;
    }

    /**
     * Upsert d'une ligne de recette : si une entite existe deja pour ce couple
     * article/produit (desactivee a l'etape precedente), on la reactive et met
     * a jour la quantite ; sinon insert. Verifie aussi que le produit existe
     * dans le tenant courant.
     *
     * <p>Tour 40bis (refactor H10).</p>
     */
    private RecetteArticle upsertLigne(Long articleId, List<RecetteArticle> existantes,
                                       LigneRecetteDto dto) {
        // Verifie que le produit existe (tenant courant)
        produitRepository.findById(dto.produitId())
                .orElseThrow(() -> new ResourceNotFoundException("error.produit.notFound"));

        RecetteArticle entity = existantes.stream()
                .filter(r -> r.getProduitId().equals(dto.produitId()))
                .findFirst()
                .orElseGet(() -> {
                    RecetteArticle r = new RecetteArticle();
                    r.setArticleId(articleId);
                    r.setProduitId(dto.produitId());
                    return r;
                });
        entity.setQuantiteParUnite(dto.quantiteParUnite());
        entity.setUnite(dto.unite());
        entity.setNote(dto.note());
        entity.setActif(Boolean.TRUE);
        return recetteRepository.save(entity);
    }

    /**
     * Valide que l'article et le produit existent dans le tenant courant.
     * Hibernate filtre via {@code @TenantId} : un id d'un autre hotel renvoie
     * {@code Optional.empty} et leve {@link ResourceNotFoundException}.
     */
    private void validateArticleAndProduit(Long articleId, Long produitId) {
        articleRepository.findById(articleId)
                .orElseThrow(() -> new ResourceNotFoundException("error.articleMenu.notFound"));
        produitRepository.findById(produitId)
                .orElseThrow(() -> new ResourceNotFoundException("error.produit.notFound"));
    }
}

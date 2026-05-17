package com.cityprojects.citybackend.service.restaurant;

import com.cityprojects.citybackend.common.tenant.RequireTenant;
import com.cityprojects.citybackend.dto.restaurant.ArticleMenuCreateDto;
import com.cityprojects.citybackend.dto.restaurant.ArticleMenuDto;
import com.cityprojects.citybackend.dto.restaurant.ArticleMenuUpdateDto;
import com.cityprojects.citybackend.entity.restaurant.ArticleMenu;
import com.cityprojects.citybackend.entity.restaurant.CategorieMenu;
import com.cityprojects.citybackend.entity.restaurant.StatutArticle;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.exception.ResourceNotFoundException;
import com.cityprojects.citybackend.mapper.restaurant.ArticleMenuMapper;
import com.cityprojects.citybackend.repository.restaurant.ArticleMenuRepository;
import com.cityprojects.citybackend.repository.restaurant.CategorieMenuRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;

/**
 * Implementation de {@link ArticleMenuService}.
 *
 * <p>Conventions :
 * <ul>
 *   <li>{@code @RequireTenant} au niveau classe.</li>
 *   <li>{@code @Transactional(readOnly = true)} a la classe, override en ecriture.</li>
 *   <li>Aucun {@code setHotelId} : Hibernate populate via le resolver.</li>
 *   <li>La coherence categorie/article est verifiee par lecture explicite : si la
 *       categorie n'est pas accessible (autre tenant), Hibernate renverra
 *       {@code Optional.empty()} et l'on leve {@link ResourceNotFoundException}.</li>
 * </ul>
 *
 * <h3>Transitions de statut autorisees (Tour 25bis F4)</h3>
 * <pre>
 *   ACTIF    -&gt; RUPTURE / INACTIF
 *   RUPTURE  -&gt; ACTIF   / INACTIF
 *   INACTIF  -&gt; ACTIF
 * </pre>
 * <p>Une transition vers le meme statut (no-op) est acceptee silencieusement.</p>
 */
@Service
@RequireTenant
@Transactional(readOnly = true)
public class ArticleMenuServiceImpl implements ArticleMenuService {

    private static final Logger logger = LoggerFactory.getLogger(ArticleMenuServiceImpl.class);

    /** Transitions valides de statut article (Tour 25bis F4). */
    private static final Map<StatutArticle, Set<StatutArticle>> TRANSITIONS_VALIDES = Map.of(
            StatutArticle.ACTIF,   Set.of(StatutArticle.RUPTURE, StatutArticle.INACTIF),
            StatutArticle.RUPTURE, Set.of(StatutArticle.ACTIF,   StatutArticle.INACTIF),
            StatutArticle.INACTIF, Set.of(StatutArticle.ACTIF));

    private final ArticleMenuRepository articleRepository;
    private final CategorieMenuRepository categorieRepository;
    private final ArticleMenuMapper mapper;

    public ArticleMenuServiceImpl(ArticleMenuRepository articleRepository,
                                  CategorieMenuRepository categorieRepository,
                                  ArticleMenuMapper mapper) {
        this.articleRepository = articleRepository;
        this.categorieRepository = categorieRepository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public ArticleMenuDto create(ArticleMenuCreateDto dto) {
        logger.info("Creation article menu : code={}, nom={}", dto.codeArticle(), dto.nom());

        if (articleRepository.existsByCodeArticle(dto.codeArticle())) {
            throw new BusinessException("error.articleMenu.code.alreadyExists");
        }
        // Verifier que la categorie existe ET appartient au tenant courant (Hibernate filtre).
        CategorieMenu categorie = categorieRepository.findById(dto.categorieId())
                .orElseThrow(() -> new ResourceNotFoundException("error.categorieMenu.notFound"));
        if (!Boolean.TRUE.equals(categorie.getActif())) {
            throw new BusinessException("error.categorieMenu.inactive");
        }

        ArticleMenu entity = mapper.toEntity(dto);
        entity.setActif(Boolean.TRUE);
        entity.setStatut(StatutArticle.ACTIF);
        if (entity.getDisponible() == null) {
            entity.setDisponible(Boolean.TRUE);
        }
        // PAS de setHotelId : Hibernate s'en charge.
        return enrichDto(articleRepository.save(entity));
    }

    @Override
    @Transactional
    public ArticleMenuDto update(Long articleId, ArticleMenuUpdateDto dto) {
        logger.info("Modification article menu id={}", articleId);
        ArticleMenu entity = articleRepository.findById(articleId)
                .orElseThrow(() -> new ResourceNotFoundException("error.articleMenu.notFound"));

        // Si la categorie change, verifier qu'elle existe dans le tenant courant.
        if (!entity.getCategorieId().equals(dto.categorieId())) {
            CategorieMenu categorie = categorieRepository.findById(dto.categorieId())
                    .orElseThrow(() -> new ResourceNotFoundException("error.categorieMenu.notFound"));
            if (!Boolean.TRUE.equals(categorie.getActif())) {
                throw new BusinessException("error.categorieMenu.inactive");
            }
            entity.setCategorieId(dto.categorieId());
        }

        entity.setNom(dto.nom());
        entity.setDescription(dto.description());
        entity.setPrix(dto.prix());
        entity.setImageUrl(dto.imageUrl());
        if (dto.disponible() != null) {
            entity.setDisponible(dto.disponible());
        }
        return enrichDto(articleRepository.save(entity));
    }

    @Override
    public ArticleMenuDto findById(Long articleId) {
        ArticleMenu entity = articleRepository.findById(articleId)
                .orElseThrow(() -> new ResourceNotFoundException("error.articleMenu.notFound"));
        return enrichDto(entity);
    }

    @Override
    public Page<ArticleMenuDto> search(String recherche, Long categorieId, Pageable pageable) {
        Page<ArticleMenu> page = articleRepository.search(recherche, categorieId, pageable);
        return enrichPage(page);
    }

    @Override
    public java.util.List<ArticleMenuDto> findDisponibles(Long categorieId) {
        java.util.List<ArticleMenu> entities = (categorieId != null)
                ? articleRepository.findByCategorieIdAndActifTrueAndStatutOrderByNomAsc(
                        categorieId, StatutArticle.ACTIF)
                : articleRepository.findByActifTrueAndStatutOrderByNomAsc(StatutArticle.ACTIF);
        return enrichList(entities);
    }

    /** Enrichit 1 article avec nomCategorie (1 SELECT). */
    private ArticleMenuDto enrichDto(ArticleMenu entity) {
        String nomCat = (entity.getCategorieId() != null)
                ? categorieRepository.findById(entity.getCategorieId())
                        .map(CategorieMenu::getNom).orElse(null)
                : null;
        return mapper.toDto(entity).withResolvedNames(nomCat);
    }

    /** Enrichit une liste d'articles en batch (1 SELECT IN sur les catégories). */
    private java.util.List<ArticleMenuDto> enrichList(java.util.List<ArticleMenu> entities) {
        java.util.Map<Long, String> nomsCategories = batchNomsCategories(entities);
        return entities.stream()
                .map(a -> mapper.toDto(a).withResolvedNames(nomsCategories.get(a.getCategorieId())))
                .toList();
    }

    /** Variante {@code Page} de {@link #enrichList}. */
    private Page<ArticleMenuDto> enrichPage(Page<ArticleMenu> page) {
        java.util.Map<Long, String> nomsCategories = batchNomsCategories(page.getContent());
        return page.map(a -> mapper.toDto(a).withResolvedNames(nomsCategories.get(a.getCategorieId())));
    }

    /** Batch lookup catégorie → nom (anti-N+1). */
    private java.util.Map<Long, String> batchNomsCategories(java.util.List<ArticleMenu> entities) {
        java.util.Set<Long> categorieIds = entities.stream()
                .map(ArticleMenu::getCategorieId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        if (categorieIds.isEmpty()) return java.util.Map.of();
        return categorieRepository.findAllById(categorieIds).stream()
                .collect(java.util.stream.Collectors.toMap(CategorieMenu::getCategorieId, CategorieMenu::getNom));
    }

    @Override
    @Transactional
    public ArticleMenuDto changeStatut(Long articleId, StatutArticle nouveauStatut) {
        logger.info("Changement statut article id={} -> {}", articleId, nouveauStatut);
        if (nouveauStatut == null) {
            throw new BusinessException("error.articleMenu.statut.required");
        }
        ArticleMenu entity = articleRepository.findById(articleId)
                .orElseThrow(() -> new ResourceNotFoundException("error.articleMenu.notFound"));
        validateTransition(entity.getStatut(), nouveauStatut);
        entity.setStatut(nouveauStatut);
        return enrichDto(articleRepository.save(entity));
    }

    /**
     * Verifie que la transition demandee respecte la matrice
     * {@link #TRANSITIONS_VALIDES}. Une transition vers le meme statut (no-op)
     * est acceptee silencieusement.
     *
     * @throws BusinessException si la transition n'est pas autorisee
     *         (cle i18n {@code error.articleMenu.transition.invalide}).
     */
    private void validateTransition(StatutArticle current, StatutArticle target) {
        if (current == target) {
            return; // no-op accepte
        }
        Set<StatutArticle> autorisees = TRANSITIONS_VALIDES.getOrDefault(current, Set.of());
        if (!autorisees.contains(target)) {
            throw new BusinessException("error.articleMenu.transition.invalide");
        }
    }

    @Override
    @Transactional
    public void deactivate(Long articleId) {
        logger.info("Desactivation article menu id={}", articleId);
        ArticleMenu entity = articleRepository.findById(articleId)
                .orElseThrow(() -> new ResourceNotFoundException("error.articleMenu.notFound"));
        entity.setActif(Boolean.FALSE);
        entity.setStatut(StatutArticle.INACTIF);
        articleRepository.save(entity);
    }

    @Override
    @Transactional
    public void reactivate(Long articleId) {
        logger.info("Reactivation article menu id={}", articleId);
        ArticleMenu entity = articleRepository.findById(articleId)
                .orElseThrow(() -> new ResourceNotFoundException("error.articleMenu.notFound"));
        entity.setActif(Boolean.TRUE);
        entity.setStatut(StatutArticle.ACTIF);
        articleRepository.save(entity);
    }
}

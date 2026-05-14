package com.cityprojects.citybackend.service.reporting;

import com.cityprojects.citybackend.common.tenant.RequireTenant;
import com.cityprojects.citybackend.dto.reporting.TicketMarginDto;
import com.cityprojects.citybackend.dto.reporting.TicketMarginDto.ArticleMargeDto;
import com.cityprojects.citybackend.entity.inventory.Produit;
import com.cityprojects.citybackend.entity.restaurant.ArticleMenu;
import com.cityprojects.citybackend.entity.restaurant.LigneCommande;
import com.cityprojects.citybackend.entity.restaurant.RecetteArticle;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.repository.inventory.ProduitRepository;
import com.cityprojects.citybackend.repository.restaurant.ArticleMenuRepository;
import com.cityprojects.citybackend.repository.restaurant.CommandeRepository;
import com.cityprojects.citybackend.repository.restaurant.LigneCommandeRepository;
import com.cityprojects.citybackend.repository.restaurant.RecetteArticleRepository;
import com.cityprojects.citybackend.service.reporting.export.XlsxExportService;
import com.cityprojects.citybackend.service.reporting.export.XlsxExportService.ColumnSpec;
import com.cityprojects.citybackend.service.reporting.export.XlsxExportService.ColumnType;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Implementation R-RES-003 — Ticket moyen + marge par article (Tour 41 P2).
 *
 * <p>Marge reelle calculee a partir de {@link RecetteArticle} (Tour 25) et
 * {@link Produit#getPrixUnitaire()} (palier 1 sans {@code prixUnitaireMoyen}).</p>
 */
@Service
@RequireTenant
@Transactional(readOnly = true)
public class TicketMarginReportServiceImpl implements TicketMarginReportService {

    private static final ZoneId NOUAKCHOTT = ZoneId.of("Africa/Nouakchott");

    private final CommandeRepository commandeRepository;
    private final LigneCommandeRepository ligneCommandeRepository;
    private final RecetteArticleRepository recetteArticleRepository;
    private final ArticleMenuRepository articleMenuRepository;
    private final ProduitRepository produitRepository;
    private final XlsxExportService xlsxExportService;

    public TicketMarginReportServiceImpl(CommandeRepository commandeRepository,
                                         LigneCommandeRepository ligneCommandeRepository,
                                         RecetteArticleRepository recetteArticleRepository,
                                         ArticleMenuRepository articleMenuRepository,
                                         ProduitRepository produitRepository,
                                         XlsxExportService xlsxExportService) {
        this.commandeRepository = commandeRepository;
        this.ligneCommandeRepository = ligneCommandeRepository;
        this.recetteArticleRepository = recetteArticleRepository;
        this.articleMenuRepository = articleMenuRepository;
        this.produitRepository = produitRepository;
        this.xlsxExportService = xlsxExportService;
    }

    @Override
    @Cacheable(value = "ticket-margin",
            key = "T(com.cityprojects.citybackend.common.tenant.TenantContext).get() + '-' + #from + '-' + #to")
    public TicketMarginDto computeMargin(LocalDate from, LocalDate to) {
        validate(from, to);

        Instant start = from.atStartOfDay(NOUAKCHOTT).toInstant();
        Instant end = to.atStartOfDay(NOUAKCHOTT).toInstant();

        // Ticket moyen
        Object[] tm = commandeRepository.aggregateTicketMoyen(start, end);
        long nbCommandes = toLong(tm, 0);
        BigDecimal caTotal = toDecimal(tm, 1);
        BigDecimal ticketMoyen = nbCommandes <= 0L
                ? BigDecimal.ZERO.setScale(2)
                : caTotal.divide(BigDecimal.valueOf(nbCommandes), 2, RoundingMode.HALF_UP);

        // Marge reelle par article
        List<LigneCommande> lignes = ligneCommandeRepository.findLignesOnRange(start, end);
        Set<Long> articleIds = new HashSet<>();
        for (LigneCommande l : lignes) {
            if (l.getArticleId() != null) {
                articleIds.add(l.getArticleId());
            }
        }

        List<ArticleMargeDto> marges = new ArrayList<>(articleIds.size());
        Map<Long, BigDecimal> prixProduitCache = new HashMap<>();

        for (Long articleId : articleIds) {
            Optional<ArticleMenu> articleOpt = articleMenuRepository.findById(articleId);
            if (articleOpt.isEmpty()) {
                continue;
            }
            ArticleMenu article = articleOpt.get();
            BigDecimal prixVente = nz(article.getPrix());
            BigDecimal coutMatiere = computeCoutMatiere(articleId, prixProduitCache);
            BigDecimal margeUnitaire = prixVente.subtract(coutMatiere);
            BigDecimal margePct = prixVente.compareTo(BigDecimal.ZERO) <= 0
                    ? BigDecimal.ZERO.setScale(2)
                    : margeUnitaire.multiply(BigDecimal.valueOf(100))
                            .divide(prixVente, 2, RoundingMode.HALF_UP);

            marges.add(new ArticleMargeDto(
                    articleId,
                    article.getNom(),
                    prixVente,
                    coutMatiere,
                    margeUnitaire.setScale(2, RoundingMode.HALF_UP),
                    margePct));
        }

        // Tri par marge unitaire decroissante
        marges.sort((a, b) -> b.margeUnitaire().compareTo(a.margeUnitaire()));

        return new TicketMarginDto(from, to, nbCommandes, caTotal, ticketMoyen, marges);
    }

    @Override
    public byte[] exportXlsx(LocalDate from, LocalDate to) {
        TicketMarginDto dto = computeMargin(from, to);
        List<ColumnSpec<ArticleMargeDto>> columns = List.of(
                new ColumnSpec<>("Article", ColumnType.TEXT, ArticleMargeDto::libelle),
                new ColumnSpec<>("Prix vente", ColumnType.MONEY, ArticleMargeDto::prixVente),
                new ColumnSpec<>("Cout matiere", ColumnType.MONEY, ArticleMargeDto::coutMatiere),
                new ColumnSpec<>("Marge unitaire", ColumnType.MONEY, ArticleMargeDto::margeUnitaire),
                new ColumnSpec<>("Marge %", ColumnType.DECIMAL, ArticleMargeDto::margePourcentage));
        return xlsxExportService.export("Ticket_Marge", columns, dto.marges());
    }

    /**
     * Cout matiere = somme sur les recettes ACTIVES :
     * {@code quantiteParUnite × Produit.prixUnitaire}. {@code prixUnitaireMoyen}
     * n'existe pas au palier 1 (fallback documente).
     */
    private BigDecimal computeCoutMatiere(Long articleId, Map<Long, BigDecimal> prixProduitCache) {
        List<RecetteArticle> recettes = recetteArticleRepository
                .findByArticleIdAndActifTrue(articleId);
        BigDecimal total = BigDecimal.ZERO;
        for (RecetteArticle r : recettes) {
            BigDecimal prix = prixProduitCache.computeIfAbsent(r.getProduitId(), pid ->
                    produitRepository.findById(pid)
                            .map(Produit::getPrixUnitaire)
                            .map(p -> p == null ? BigDecimal.ZERO : p)
                            .orElse(BigDecimal.ZERO));
            BigDecimal qte = nz(r.getQuantiteParUnite());
            total = total.add(prix.multiply(qte));
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static long toLong(Object[] row, int index) {
        if (row == null || row.length <= index || row[index] == null) {
            return 0L;
        }
        Object v = row[index];
        return v instanceof Number n ? n.longValue() : 0L;
    }

    private static BigDecimal toDecimal(Object[] row, int index) {
        if (row == null || row.length <= index || row[index] == null) {
            return BigDecimal.ZERO;
        }
        Object v = row[index];
        if (v instanceof BigDecimal bd) {
            return bd;
        }
        if (v instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        return BigDecimal.ZERO;
    }

    private static void validate(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new BusinessException("error.report.dateRange.required");
        }
        if (!from.isBefore(to)) {
            throw new BusinessException("error.report.dateRange.invalid");
        }
    }
}

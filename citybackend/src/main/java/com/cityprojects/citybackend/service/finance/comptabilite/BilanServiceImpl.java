package com.cityprojects.citybackend.service.finance.comptabilite;

import com.cityprojects.citybackend.common.tenant.RequireTenant;
import com.cityprojects.citybackend.dto.finance.comptabilite.BilanDto;
import com.cityprojects.citybackend.dto.finance.comptabilite.LigneBilanDto;
import com.cityprojects.citybackend.dto.finance.comptabilite.RubriqueBilanDto;
import com.cityprojects.citybackend.entity.finance.Exercice;
import com.cityprojects.citybackend.entity.finance.NatureCompte;
import com.cityprojects.citybackend.entity.finance.PlanComptableGeneral;
import com.cityprojects.citybackend.entity.finance.SensLigne;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.exception.ResourceNotFoundException;
import com.cityprojects.citybackend.repository.finance.ExerciceRepository;
import com.cityprojects.citybackend.repository.finance.LigneEcritureRepository;
import com.cityprojects.citybackend.repository.finance.PlanComptableGeneralRepository;
import com.cityprojects.citybackend.service.finance.comptabilite.pdf.EtatsPdfHelper;
import com.cityprojects.citybackend.service.reporting.export.XlsxExportService;
import com.cityprojects.citybackend.service.reporting.export.XlsxExportService.ColumnSpec;
import com.cityprojects.citybackend.service.reporting.export.XlsxExportService.ColumnType;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementation de {@link BilanService} (B5).
 *
 * <p>Bilan SYSCOHADA simplifie a la {@code dateArrete}. Plage de calcul des
 * soldes = {@code [exercice.dateDebut, dateArrete]} (bornes inclusives).</p>
 *
 * <p>Le {@code resultatNet} est calcule a partir des comptes de classe 6
 * (charges) et 7 (produits) sur l'exercice : si positif, profit (au passif
 * en augmentation des capitaux propres) ; si negatif, perte (au passif en
 * diminution).</p>
 */
@Service
@RequireTenant
@Transactional(readOnly = true)
public class BilanServiceImpl implements BilanService {

    private static final Logger log = LoggerFactory.getLogger(BilanServiceImpl.class);
    private static final BigDecimal TOLERANCE = new BigDecimal("0.01");

    private final LigneEcritureRepository ligneEcritureRepository;
    private final PlanComptableGeneralRepository pcgRepository;
    private final ExerciceRepository exerciceRepository;
    private final XlsxExportService xlsxExportService;

    public BilanServiceImpl(LigneEcritureRepository ligneEcritureRepository,
                            PlanComptableGeneralRepository pcgRepository,
                            ExerciceRepository exerciceRepository,
                            XlsxExportService xlsxExportService) {
        this.ligneEcritureRepository = ligneEcritureRepository;
        this.pcgRepository = pcgRepository;
        this.exerciceRepository = exerciceRepository;
        this.xlsxExportService = xlsxExportService;
    }

    @Override
    public BilanDto compute(Long exerciceId, LocalDate dateArrete) {
        if (exerciceId == null) {
            throw new BusinessException("error.etat.exerciceIdRequired");
        }
        Exercice exercice = exerciceRepository.findById(exerciceId)
                .orElseThrow(() -> new ResourceNotFoundException("error.exercice.notFound"));
        LocalDate dArrete = dateArrete != null ? dateArrete : exercice.getDateFin();
        if (dArrete.isBefore(exercice.getDateDebut())) {
            throw new BusinessException("error.etat.dateRangeInvalide");
        }
        LocalDate dDebut = exercice.getDateDebut();

        // ACTIF : classes 2, 3, 4 (ACTIF), 5 (debiteur)
        List<RubriqueBilanDto> actif = new ArrayList<>();
        actif.add(buildRubriqueByClasse("AI", "Immobilisations corporelles", 2, dDebut, dArrete, RubriqueOrientation.ACTIF));
        actif.add(buildRubriqueByClasse("AS", "Stocks et en-cours", 3, dDebut, dArrete, RubriqueOrientation.ACTIF));
        actif.add(buildRubriqueByClasse("AC", "Creances et emplois assimiles", 4, dDebut, dArrete, RubriqueOrientation.ACTIF));
        actif.add(buildRubriqueByClasse("AT", "Tresorerie - Actif", 5, dDebut, dArrete, RubriqueOrientation.ACTIF));

        // PASSIF : classes 1, 4 (PASSIF), 5 (crediteur)
        List<RubriqueBilanDto> passif = new ArrayList<>();
        passif.add(buildRubriqueByClasse("PC", "Capitaux propres", 1, dDebut, dArrete, RubriqueOrientation.PASSIF));
        passif.add(buildRubriqueByClasse("PD", "Dettes circulantes", 4, dDebut, dArrete, RubriqueOrientation.PASSIF));
        passif.add(buildRubriqueByClasse("PT", "Tresorerie - Passif", 5, dDebut, dArrete, RubriqueOrientation.PASSIF));

        // Resultat net = produits (classe 7) - charges (classe 6)
        BigDecimal totalProduits = sumClasseSolde(7, dDebut, dArrete, true);
        BigDecimal totalCharges = sumClasseSolde(6, dDebut, dArrete, false);
        BigDecimal resultatNet = totalProduits.subtract(totalCharges);

        // Pousse le resultat au passif (positif = profit, negatif = perte)
        List<LigneBilanDto> resultatLignes = List.of(new LigneBilanDto(
                "RES", resultatNet.signum() >= 0 ? "Resultat net (benefice)" : "Resultat net (perte)",
                resultatNet.setScale(2, RoundingMode.HALF_UP)));
        passif.add(new RubriqueBilanDto("PR", "Resultat de l'exercice", 1,
                resultatLignes, resultatNet.setScale(2, RoundingMode.HALF_UP)));

        BigDecimal totalActif = actif.stream()
                .map(RubriqueBilanDto::montant)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalPassif = passif.stream()
                .map(RubriqueBilanDto::montant)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal ecart = totalActif.subtract(totalPassif).abs();
        if (ecart.compareTo(TOLERANCE) > 0) {
            log.warn("Bilan desequilibre exercice={} arrete={} : ACTIF = {}, PASSIF = {}, ecart = {}",
                    exercice.getCode(), dArrete, totalActif, totalPassif, ecart);
        }

        return new BilanDto(
                exercice.getId(),
                exercice.getCode(),
                dArrete,
                actif,
                totalActif.setScale(2, RoundingMode.HALF_UP),
                passif,
                totalPassif.setScale(2, RoundingMode.HALF_UP),
                resultatNet.setScale(2, RoundingMode.HALF_UP),
                Instant.now());
    }

    /**
     * Construit une rubrique de bilan pour une classe : agrege les soldes des
     * comptes de cette classe sur la periode, ne retient que ceux dont le
     * solde est dans l'orientation demandee (ACTIF = solde debiteur,
     * PASSIF = solde crediteur).
     */
    private RubriqueBilanDto buildRubriqueByClasse(String code, String libelle, int classe,
                                                    LocalDate dDebut, LocalDate dArrete,
                                                    RubriqueOrientation orientation) {
        List<String> codes = ligneEcritureRepository
                .findDistinctCompteCodesByDateBetweenAndPrefixe(dDebut, dArrete, classe + "%");
        List<LigneBilanDto> lignes = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (String compteCode : codes) {
            PlanComptableGeneral pcg = pcgRepository.findByCompteCode(compteCode).orElse(null);
            if (pcg == null) {
                continue;
            }
            // Pour la classe 4, filtre par nature (ACTIF / PASSIF / MIXTE distinguent
            // creances vs dettes)
            if (classe == 4 && orientation == RubriqueOrientation.ACTIF
                    && pcg.getNature() == NatureCompte.PASSIF) {
                continue;
            }
            if (classe == 4 && orientation == RubriqueOrientation.PASSIF
                    && pcg.getNature() == NatureCompte.ACTIF) {
                continue;
            }
            BigDecimal d = nz(ligneEcritureRepository.sumByCompteCodeAndDateBetween(
                    compteCode, dDebut, dArrete, SensLigne.DEBIT));
            BigDecimal c = nz(ligneEcritureRepository.sumByCompteCodeAndDateBetween(
                    compteCode, dDebut, dArrete, SensLigne.CREDIT));
            BigDecimal solde = d.subtract(c);
            // Orientation : ACTIF retient solde > 0 (debiteur),
            // PASSIF retient -solde > 0 (crediteur, on inverse pour rendre positif)
            BigDecimal montant;
            if (orientation == RubriqueOrientation.ACTIF) {
                if (solde.signum() <= 0) {
                    continue;
                }
                montant = solde;
            } else {
                if (solde.signum() >= 0) {
                    continue;
                }
                montant = solde.negate();
            }
            lignes.add(new LigneBilanDto(compteCode, pcg.getLibelle(),
                    montant.setScale(2, RoundingMode.HALF_UP)));
            total = total.add(montant);
        }
        return new RubriqueBilanDto(code, libelle, classe, lignes,
                total.setScale(2, RoundingMode.HALF_UP));
    }

    /**
     * Somme des soldes d'une classe (utilise pour le resultat net).
     *
     * @param classe         classe SYSCOHADA (6 ou 7)
     * @param dDebut         date debut periode
     * @param dFin           date fin periode
     * @param sensCrediteur  true si on cherche le solde crediteur (produits classe 7),
     *                       false si on cherche le solde debiteur (charges classe 6)
     */
    private BigDecimal sumClasseSolde(int classe, LocalDate dDebut, LocalDate dFin,
                                       boolean sensCrediteur) {
        List<String> codes = ligneEcritureRepository
                .findDistinctCompteCodesByDateBetweenAndPrefixe(dDebut, dFin, classe + "%");
        BigDecimal total = BigDecimal.ZERO;
        for (String code : codes) {
            BigDecimal d = nz(ligneEcritureRepository.sumByCompteCodeAndDateBetween(
                    code, dDebut, dFin, SensLigne.DEBIT));
            BigDecimal c = nz(ligneEcritureRepository.sumByCompteCodeAndDateBetween(
                    code, dDebut, dFin, SensLigne.CREDIT));
            BigDecimal solde = sensCrediteur ? c.subtract(d) : d.subtract(c);
            if (solde.signum() > 0) {
                total = total.add(solde);
            }
        }
        return total;
    }

    @Override
    public byte[] exportXlsx(Long exerciceId, LocalDate dateArrete) {
        BilanDto dto = compute(exerciceId, dateArrete);

        List<FlatRow> rows = new ArrayList<>();
        rows.add(FlatRow.section("ACTIF"));
        for (RubriqueBilanDto r : dto.actif()) {
            rows.add(FlatRow.rubrique(r));
            for (LigneBilanDto l : r.lignes()) {
                rows.add(FlatRow.ligne(l));
            }
        }
        rows.add(FlatRow.total("TOTAL ACTIF", dto.totalActif()));
        rows.add(FlatRow.blank());
        rows.add(FlatRow.section("PASSIF"));
        for (RubriqueBilanDto r : dto.passif()) {
            rows.add(FlatRow.rubrique(r));
            for (LigneBilanDto l : r.lignes()) {
                rows.add(FlatRow.ligne(l));
            }
        }
        rows.add(FlatRow.total("TOTAL PASSIF", dto.totalPassif()));

        List<ColumnSpec<FlatRow>> columns = List.of(
                new ColumnSpec<>("Section", ColumnType.TEXT, FlatRow::section),
                new ColumnSpec<>("Compte", ColumnType.TEXT, FlatRow::compteCode),
                new ColumnSpec<>("Libelle", ColumnType.TEXT, FlatRow::libelle),
                new ColumnSpec<>("Montant", ColumnType.MONEY, FlatRow::montant));

        return xlsxExportService.export("Bilan " + dto.exerciceCode(), columns, rows);
    }

    @Override
    public byte[] exportPdf(Long exerciceId, LocalDate dateArrete) {
        BilanDto dto = compute(exerciceId, dateArrete);
        Document doc = EtatsPdfHelper.newPortraitDocument();
        ByteArrayOutputStream out = EtatsPdfHelper.open(doc);
        try {
            String periodeLib = "Arrete au " + dto.dateArrete().format(EtatsPdfHelper.DATE_FMT)
                    + " (Exercice " + dto.exerciceCode() + ")";
            EtatsPdfHelper.addHeader(doc, "Bilan SYSCOHADA simplifie", "", periodeLib);

            float[] widths = {1f, 1f};
            PdfPTable two = new PdfPTable(widths);
            try {
                two.setWidths(widths);
                two.setWidthPercentage(100f);
            } catch (Exception e) {
                throw new BusinessException("error.etat.pdf.failed");
            }
            // Cellules avec sous-tableaux
            PdfPCell actifCell = new PdfPCell(buildSectionTable("ACTIF", dto.actif(), dto.totalActif()));
            actifCell.setBorder(0);
            actifCell.setVerticalAlignment(Element.ALIGN_TOP);
            two.addCell(actifCell);

            PdfPCell passifCell = new PdfPCell(buildSectionTable("PASSIF", dto.passif(), dto.totalPassif()));
            passifCell.setBorder(0);
            passifCell.setVerticalAlignment(Element.ALIGN_TOP);
            two.addCell(passifCell);

            EtatsPdfHelper.addTable(doc, two);
            EtatsPdfHelper.addParagraph(doc,
                    "Resultat net de l'exercice : " + EtatsPdfHelper.money(dto.resultatNet()) + " MRU",
                    11f, true);
            return EtatsPdfHelper.close(doc, out);
        } catch (RuntimeException e) {
            log.error("Bilan PDF export failed", e);
            if (doc.isOpen()) {
                doc.close();
            }
            throw e;
        }
    }

    private PdfPTable buildSectionTable(String titre, List<RubriqueBilanDto> rubriques,
                                         BigDecimal total) {
        float[] widths = {2f, 4.5f, 2.5f};
        PdfPTable table = EtatsPdfHelper.newTable(widths);
        table.setSpacingBefore(0f);
        // Section title sur 3 colonnes
        table.addCell(EtatsPdfHelper.sectionCell(titre, 3));
        table.addCell(EtatsPdfHelper.headerCell("Compte"));
        table.addCell(EtatsPdfHelper.headerCell("Libelle"));
        table.addCell(EtatsPdfHelper.headerCell("Montant"));
        for (RubriqueBilanDto r : rubriques) {
            table.addCell(EtatsPdfHelper.sectionCell(r.libelle(), 2));
            table.addCell(EtatsPdfHelper.totalMoneyCell(r.montant()));
            for (LigneBilanDto l : r.lignes()) {
                table.addCell(EtatsPdfHelper.textCell(l.compteCode()));
                table.addCell(EtatsPdfHelper.textCell(l.compteLibelle()));
                table.addCell(EtatsPdfHelper.moneyCell(l.montant()));
            }
        }
        table.addCell(EtatsPdfHelper.totalLabelCell("TOTAL " + titre));
        table.addCell(EtatsPdfHelper.totalLabelCell(""));
        table.addCell(EtatsPdfHelper.totalMoneyCell(total));
        return table;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private enum RubriqueOrientation { ACTIF, PASSIF }

    /** Ligne aplatie pour l'export XLSX. */
    private record FlatRow(String section, String compteCode, String libelle, BigDecimal montant) {
        static FlatRow section(String name) {
            return new FlatRow(name, null, null, null);
        }
        static FlatRow rubrique(RubriqueBilanDto r) {
            return new FlatRow(null, null, r.libelle(), r.montant());
        }
        static FlatRow ligne(LigneBilanDto l) {
            return new FlatRow(null, l.compteCode(), l.compteLibelle(), l.montant());
        }
        static FlatRow total(String label, BigDecimal value) {
            return new FlatRow(null, null, label, value);
        }
        static FlatRow blank() {
            return new FlatRow(null, null, null, null);
        }
    }
}

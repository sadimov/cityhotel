package com.cityprojects.citybackend.service.finance.comptabilite;

import com.cityprojects.citybackend.common.tenant.RequireTenant;
import com.cityprojects.citybackend.dto.finance.comptabilite.BalanceComptableDto;
import com.cityprojects.citybackend.dto.finance.comptabilite.BalanceFilterDto;
import com.cityprojects.citybackend.dto.finance.comptabilite.LigneBalanceDto;
import com.cityprojects.citybackend.entity.finance.Exercice;
import com.cityprojects.citybackend.entity.finance.NatureCompte;
import com.cityprojects.citybackend.entity.finance.PlanComptableGeneral;
import com.cityprojects.citybackend.entity.finance.SensLigne;
import com.cityprojects.citybackend.entity.finance.SensNormal;
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
import java.util.Map;
import java.util.Optional;

/**
 * Implementation de {@link BalanceComptableService} (B5).
 *
 * <p>Tenant-aware via {@code @RequireTenant} - les requetes sur
 * {@link LigneEcritureRepository} sont filtrees automatiquement
 * {@code WHERE hotel_id = ?} grace au {@code @TenantId}.</p>
 */
@Service
@RequireTenant
@Transactional(readOnly = true)
public class BalanceComptableServiceImpl implements BalanceComptableService {

    private static final Logger log = LoggerFactory.getLogger(BalanceComptableServiceImpl.class);
    private static final BigDecimal TOLERANCE = new BigDecimal("0.01");

    private final LigneEcritureRepository ligneEcritureRepository;
    private final PlanComptableGeneralRepository pcgRepository;
    private final ExerciceRepository exerciceRepository;
    private final XlsxExportService xlsxExportService;

    public BalanceComptableServiceImpl(LigneEcritureRepository ligneEcritureRepository,
                                       PlanComptableGeneralRepository pcgRepository,
                                       ExerciceRepository exerciceRepository,
                                       XlsxExportService xlsxExportService) {
        this.ligneEcritureRepository = ligneEcritureRepository;
        this.pcgRepository = pcgRepository;
        this.exerciceRepository = exerciceRepository;
        this.xlsxExportService = xlsxExportService;
    }

    @Override
    public BalanceComptableDto compute(BalanceFilterDto filter) {
        if (filter == null) {
            throw new BusinessException("error.etat.filterRequired");
        }
        ResolvedPeriode periode = resolvePeriode(filter);
        validateClasse(filter.classe());

        List<String> comptes = (filter.classe() != null)
                ? ligneEcritureRepository.findDistinctCompteCodesByDateBetweenAndPrefixe(
                        periode.dateDebut, periode.dateFin, filter.classe() + "%")
                : ligneEcritureRepository.findDistinctCompteCodesByDateBetween(
                        periode.dateDebut, periode.dateFin);

        List<LigneBalanceDto> lignes = new ArrayList<>(comptes.size());
        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;
        BigDecimal totalSoldeDebiteur = BigDecimal.ZERO;
        BigDecimal totalSoldeCrediteur = BigDecimal.ZERO;

        for (String code : comptes) {
            BigDecimal d = nz(ligneEcritureRepository.sumByCompteCodeAndDateBetween(
                    code, periode.dateDebut, periode.dateFin, SensLigne.DEBIT));
            BigDecimal c = nz(ligneEcritureRepository.sumByCompteCodeAndDateBetween(
                    code, periode.dateDebut, periode.dateFin, SensLigne.CREDIT));

            PlanComptableGeneral pcg = pcgRepository.findByCompteCode(code).orElse(null);
            String libelle = pcg != null ? pcg.getLibelle() : code;
            int classe = pcg != null ? pcg.getClasse() : 0;
            NatureCompte nature = pcg != null ? pcg.getNature() : NatureCompte.MIXTE;
            SensNormal sensNormal = pcg != null ? pcg.getSensNormal() : SensNormal.MIXTE;

            BigDecimal[] soldes = computeSoldes(d, c, sensNormal);
            BigDecimal soldeD = soldes[0];
            BigDecimal soldeC = soldes[1];

            lignes.add(new LigneBalanceDto(code, libelle, classe, nature, sensNormal,
                    d.setScale(2, RoundingMode.HALF_UP),
                    c.setScale(2, RoundingMode.HALF_UP),
                    soldeD.setScale(2, RoundingMode.HALF_UP),
                    soldeC.setScale(2, RoundingMode.HALF_UP)));

            totalDebit = totalDebit.add(d);
            totalCredit = totalCredit.add(c);
            totalSoldeDebiteur = totalSoldeDebiteur.add(soldeD);
            totalSoldeCrediteur = totalSoldeCrediteur.add(soldeC);
        }

        BigDecimal ecart = totalSoldeDebiteur.subtract(totalSoldeCrediteur).abs();
        if (ecart.compareTo(TOLERANCE) > 0) {
            log.warn("Balance desequilibree pour periode {} -> {} : Sigma D = {}, Sigma C = {}, ecart = {}",
                    periode.dateDebut, periode.dateFin, totalSoldeDebiteur, totalSoldeCrediteur, ecart);
        }

        return new BalanceComptableDto(
                periode.exerciceId,
                periode.exerciceCode,
                periode.dateDebut,
                periode.dateFin,
                lignes,
                totalDebit.setScale(2, RoundingMode.HALF_UP),
                totalCredit.setScale(2, RoundingMode.HALF_UP),
                totalSoldeDebiteur.setScale(2, RoundingMode.HALF_UP),
                totalSoldeCrediteur.setScale(2, RoundingMode.HALF_UP),
                Instant.now());
    }

    @Override
    public byte[] exportXlsx(BalanceFilterDto filter) {
        BalanceComptableDto dto = compute(filter);

        List<ColumnSpec<LigneBalanceDto>> columns = List.of(
                new ColumnSpec<>("Compte", ColumnType.TEXT, LigneBalanceDto::compteCode),
                new ColumnSpec<>("Libelle", ColumnType.TEXT, LigneBalanceDto::compteLibelle),
                new ColumnSpec<>("Classe", ColumnType.INTEGER, LigneBalanceDto::classe),
                new ColumnSpec<>("Nature", ColumnType.TEXT,
                        l -> l.nature() == null ? "" : l.nature().name()),
                new ColumnSpec<>("Sens normal", ColumnType.TEXT,
                        l -> l.sensNormal() == null ? "" : l.sensNormal().name()),
                new ColumnSpec<>("Debit", ColumnType.MONEY, LigneBalanceDto::totalDebit),
                new ColumnSpec<>("Credit", ColumnType.MONEY, LigneBalanceDto::totalCredit),
                new ColumnSpec<>("Solde Debiteur", ColumnType.MONEY, LigneBalanceDto::soldeDebiteur),
                new ColumnSpec<>("Solde Crediteur", ColumnType.MONEY, LigneBalanceDto::soldeCrediteur));

        List<LigneBalanceDto> rows = new ArrayList<>(dto.lignes());
        // Ligne totaux en bas
        rows.add(new LigneBalanceDto("TOTAL", "Totaux", 0, null, null,
                dto.totalDebit(), dto.totalCredit(),
                dto.totalSoldeDebiteur(), dto.totalSoldeCrediteur()));

        return xlsxExportService.export("Balance", columns, rows);
    }

    @Override
    public byte[] exportPdf(BalanceFilterDto filter) {
        BalanceComptableDto dto = compute(filter);
        Document doc = EtatsPdfHelper.newLandscapeDocument();
        ByteArrayOutputStream out = EtatsPdfHelper.open(doc);
        try {
            String periodeLib = "Periode : " + (dto.dateDebut() != null ? dto.dateDebut().format(EtatsPdfHelper.DATE_FMT) : "?")
                    + " - " + (dto.dateFin() != null ? dto.dateFin().format(EtatsPdfHelper.DATE_FMT) : "?")
                    + (dto.exerciceCode() != null ? " (Exercice " + dto.exerciceCode() + ")" : "");
            EtatsPdfHelper.addHeader(doc, "Balance comptable", "", periodeLib);

            float[] widths = {2f, 5f, 1.2f, 1.6f, 1.8f, 2.5f, 2.5f, 2.5f, 2.5f};
            PdfPTable table = EtatsPdfHelper.newTable(widths);
            table.setHeaderRows(1);
            table.addCell(EtatsPdfHelper.headerCell("Compte"));
            table.addCell(EtatsPdfHelper.headerCell("Libelle"));
            table.addCell(EtatsPdfHelper.headerCell("Cl."));
            table.addCell(EtatsPdfHelper.headerCell("Nature"));
            table.addCell(EtatsPdfHelper.headerCell("Sens"));
            table.addCell(EtatsPdfHelper.headerCell("Debit"));
            table.addCell(EtatsPdfHelper.headerCell("Credit"));
            table.addCell(EtatsPdfHelper.headerCell("Solde D"));
            table.addCell(EtatsPdfHelper.headerCell("Solde C"));

            for (LigneBalanceDto l : dto.lignes()) {
                table.addCell(EtatsPdfHelper.textCell(l.compteCode()));
                table.addCell(EtatsPdfHelper.textCell(l.compteLibelle()));
                table.addCell(EtatsPdfHelper.textCell(String.valueOf(l.classe())));
                table.addCell(EtatsPdfHelper.textCell(l.nature() == null ? "" : l.nature().name()));
                table.addCell(EtatsPdfHelper.textCell(l.sensNormal() == null ? "" : l.sensNormal().name()));
                table.addCell(EtatsPdfHelper.moneyCell(l.totalDebit()));
                table.addCell(EtatsPdfHelper.moneyCell(l.totalCredit()));
                table.addCell(EtatsPdfHelper.moneyCell(l.soldeDebiteur()));
                table.addCell(EtatsPdfHelper.moneyCell(l.soldeCrediteur()));
            }

            // Ligne totaux
            PdfPTable totals = EtatsPdfHelper.newTable(widths);
            totals.addCell(EtatsPdfHelper.totalLabelCell("TOTAL"));
            totals.addCell(EtatsPdfHelper.totalLabelCell(""));
            totals.addCell(EtatsPdfHelper.totalLabelCell(""));
            totals.addCell(EtatsPdfHelper.totalLabelCell(""));
            totals.addCell(EtatsPdfHelper.totalLabelCell(""));
            totals.addCell(EtatsPdfHelper.totalMoneyCell(dto.totalDebit()));
            totals.addCell(EtatsPdfHelper.totalMoneyCell(dto.totalCredit()));
            totals.addCell(EtatsPdfHelper.totalMoneyCell(dto.totalSoldeDebiteur()));
            totals.addCell(EtatsPdfHelper.totalMoneyCell(dto.totalSoldeCrediteur()));

            EtatsPdfHelper.addTable(doc, table);
            EtatsPdfHelper.addTable(doc, totals);
            return EtatsPdfHelper.close(doc, out);
        } catch (RuntimeException e) {
            log.error("Balance PDF export failed", e);
            if (doc.isOpen()) {
                doc.close();
            }
            throw e;
        }
    }

    /**
     * Calcule [soldeD, soldeC] selon le sens normal du compte.
     *
     * <p>Visible package pour les tests directs.</p>
     */
    static BigDecimal[] computeSoldes(BigDecimal totalDebit, BigDecimal totalCredit,
                                       SensNormal sensNormal) {
        BigDecimal d = nz(totalDebit);
        BigDecimal c = nz(totalCredit);
        BigDecimal soldeD = BigDecimal.ZERO;
        BigDecimal soldeC = BigDecimal.ZERO;
        if (sensNormal == SensNormal.MIXTE) {
            int cmp = d.compareTo(c);
            if (cmp > 0) {
                soldeD = d.subtract(c);
            } else if (cmp < 0) {
                soldeC = c.subtract(d);
            }
        } else if (sensNormal == SensNormal.DEBITEUR) {
            int cmp = d.compareTo(c);
            if (cmp >= 0) {
                soldeD = d.subtract(c);
            } else {
                // Solde anormal : credit superieur sur compte debiteur
                soldeC = c.subtract(d);
            }
        } else { // CREDITEUR
            int cmp = c.compareTo(d);
            if (cmp >= 0) {
                soldeC = c.subtract(d);
            } else {
                // Solde anormal : debit superieur sur compte crediteur
                soldeD = d.subtract(c);
            }
        }
        return new BigDecimal[]{soldeD, soldeC};
    }

    /** Resout la periode et l'exercice associes au filtre. */
    private ResolvedPeriode resolvePeriode(BalanceFilterDto filter) {
        Long exerciceId = filter.exerciceId();
        LocalDate d1 = filter.dateDebut();
        LocalDate d2 = filter.dateFin();
        String exerciceCode = null;

        if (exerciceId != null) {
            Exercice exercice = exerciceRepository.findById(exerciceId)
                    .orElseThrow(() -> new ResourceNotFoundException("error.exercice.notFound"));
            exerciceCode = exercice.getCode();
            if (d1 == null) {
                d1 = exercice.getDateDebut();
            }
            if (d2 == null) {
                d2 = exercice.getDateFin();
            }
        }

        if (d1 == null || d2 == null) {
            throw new BusinessException("error.etat.filterRequired");
        }
        if (d2.isBefore(d1)) {
            throw new BusinessException("error.etat.dateRangeInvalide");
        }
        return new ResolvedPeriode(exerciceId, exerciceCode, d1, d2);
    }

    private static void validateClasse(Integer classe) {
        if (classe != null && (classe < 1 || classe > 7)) {
            throw new BusinessException("error.etat.classeInvalide");
        }
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /**
     * Helper interne - periode resolue avec optionnellement les metadonnees
     * d'exercice. Visible package pour reutilisation par autres services B5.
     */
    record ResolvedPeriode(Long exerciceId, String exerciceCode,
                            LocalDate dateDebut, LocalDate dateFin) {
    }

    /**
     * Helper utilitaire reutilise par les autres services B5 pour resoudre une
     * periode a partir d'un exerciceId / dateDebut / dateFin.
     */
    public static Map.Entry<LocalDate, LocalDate> resolvePeriode(
            ExerciceRepository exerciceRepo, Long exerciceId,
            LocalDate dateDebut, LocalDate dateFin) {
        Optional<Exercice> exOpt = exerciceId != null
                ? exerciceRepo.findById(exerciceId)
                : Optional.empty();
        if (exerciceId != null && exOpt.isEmpty()) {
            throw new ResourceNotFoundException("error.exercice.notFound");
        }
        LocalDate d1 = dateDebut;
        LocalDate d2 = dateFin;
        if (exOpt.isPresent()) {
            if (d1 == null) {
                d1 = exOpt.get().getDateDebut();
            }
            if (d2 == null) {
                d2 = exOpt.get().getDateFin();
            }
        }
        if (d1 == null || d2 == null) {
            throw new BusinessException("error.etat.filterRequired");
        }
        if (d2.isBefore(d1)) {
            throw new BusinessException("error.etat.dateRangeInvalide");
        }
        return Map.entry(d1, d2);
    }
}

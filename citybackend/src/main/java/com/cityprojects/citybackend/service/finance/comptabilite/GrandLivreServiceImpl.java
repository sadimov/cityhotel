package com.cityprojects.citybackend.service.finance.comptabilite;

import com.cityprojects.citybackend.common.tenant.RequireTenant;
import com.cityprojects.citybackend.dto.finance.comptabilite.CompteGrandLivreDto;
import com.cityprojects.citybackend.dto.finance.comptabilite.GrandLivreDto;
import com.cityprojects.citybackend.dto.finance.comptabilite.GrandLivreFilterDto;
import com.cityprojects.citybackend.dto.finance.comptabilite.LigneGrandLivreDto;
import com.cityprojects.citybackend.entity.finance.EcritureComptable;
import com.cityprojects.citybackend.entity.finance.Exercice;
import com.cityprojects.citybackend.entity.finance.LigneEcriture;
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
import java.util.Optional;

/**
 * Implementation de {@link GrandLivreService} (B5).
 *
 * <p>Pour chaque compte (filtre par {@code compteCode} ou tous les comptes
 * mouvementes), liste les lignes d'ecriture sur la periode dans l'ordre
 * chronologique avec calcul d'un solde progressif (positif = debiteur,
 * negatif = crediteur).</p>
 *
 * <p>Le report initial est calcule comme {@code Sigma D - Sigma C} sur la
 * plage {@code [dateDebutExercice, dateDebut - 1]}. Si aucun exercice n'est
 * fourni, le report initial est 0 (la periode demarre sans antecedent).</p>
 */
@Service
@RequireTenant
@Transactional(readOnly = true)
public class GrandLivreServiceImpl implements GrandLivreService {

    private static final Logger log = LoggerFactory.getLogger(GrandLivreServiceImpl.class);

    private final LigneEcritureRepository ligneEcritureRepository;
    private final PlanComptableGeneralRepository pcgRepository;
    private final ExerciceRepository exerciceRepository;
    private final XlsxExportService xlsxExportService;

    public GrandLivreServiceImpl(LigneEcritureRepository ligneEcritureRepository,
                                  PlanComptableGeneralRepository pcgRepository,
                                  ExerciceRepository exerciceRepository,
                                  XlsxExportService xlsxExportService) {
        this.ligneEcritureRepository = ligneEcritureRepository;
        this.pcgRepository = pcgRepository;
        this.exerciceRepository = exerciceRepository;
        this.xlsxExportService = xlsxExportService;
    }

    @Override
    public GrandLivreDto compute(GrandLivreFilterDto filter) {
        if (filter == null) {
            throw new BusinessException("error.etat.filterRequired");
        }
        Resolved r = resolveAll(filter);

        List<String> comptes;
        if (filter.compteCode() != null && !filter.compteCode().isBlank()) {
            comptes = List.of(filter.compteCode());
        } else {
            comptes = ligneEcritureRepository.findDistinctCompteCodesByDateBetween(
                    r.dateDebut, r.dateFin);
        }

        List<CompteGrandLivreDto> result = new ArrayList<>(comptes.size());
        for (String code : comptes) {
            result.add(buildCompteGrandLivre(code, r));
        }

        return new GrandLivreDto(r.dateDebut, r.dateFin, result, Instant.now());
    }

    private CompteGrandLivreDto buildCompteGrandLivre(String code, Resolved r) {
        PlanComptableGeneral pcg = pcgRepository.findByCompteCode(code).orElse(null);
        String libelle = pcg != null ? pcg.getLibelle() : code;

        BigDecimal reportInitial = computeReportInitial(code, r);
        List<LigneEcriture> lignesEntity = ligneEcritureRepository
                .findByCompteCodeAndDateBetween(code, r.dateDebut, r.dateFin);

        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;
        BigDecimal soldeProgressif = reportInitial;
        List<LigneGrandLivreDto> dtos = new ArrayList<>(lignesEntity.size());

        for (LigneEcriture l : lignesEntity) {
            BigDecimal debit = (l.getSens() == SensLigne.DEBIT) ? l.getMontant() : BigDecimal.ZERO;
            BigDecimal credit = (l.getSens() == SensLigne.CREDIT) ? l.getMontant() : BigDecimal.ZERO;
            totalDebit = totalDebit.add(debit);
            totalCredit = totalCredit.add(credit);
            soldeProgressif = soldeProgressif.add(debit).subtract(credit);

            EcritureComptable ec = l.getEcriture();
            LocalDate dateComp = ec != null ? ec.getDateComptable() : null;
            String numero = ec != null ? ec.getNumero() : null;
            String journalCode = (ec != null && ec.getJournal() != null) ? ec.getJournal().getCode() : null;
            String libelleEc = (l.getLibelle() != null && !l.getLibelle().isBlank())
                    ? l.getLibelle()
                    : (ec != null ? ec.getLibelle() : null);
            String reference = ec != null ? ec.getReference() : null;

            dtos.add(new LigneGrandLivreDto(
                    dateComp, numero, journalCode, libelleEc, reference,
                    debit.setScale(2, RoundingMode.HALF_UP),
                    credit.setScale(2, RoundingMode.HALF_UP),
                    soldeProgressif.setScale(2, RoundingMode.HALF_UP)));
        }

        BigDecimal soldeFinal = reportInitial.add(totalDebit).subtract(totalCredit);
        return new CompteGrandLivreDto(code, libelle,
                reportInitial.setScale(2, RoundingMode.HALF_UP),
                dtos,
                totalDebit.setScale(2, RoundingMode.HALF_UP),
                totalCredit.setScale(2, RoundingMode.HALF_UP),
                soldeFinal.setScale(2, RoundingMode.HALF_UP));
    }

    private BigDecimal computeReportInitial(String compteCode, Resolved r) {
        // Si dateDebut == debutExercice, report initial est 0.
        // Sinon, plage = [dateDebutExercice, dateDebut - 1 jour].
        LocalDate baseDebut = (r.exerciceDateDebut != null) ? r.exerciceDateDebut : r.dateDebut;
        LocalDate baseFin = r.dateDebut.minusDays(1);
        if (baseFin.isBefore(baseDebut)) {
            return BigDecimal.ZERO;
        }
        BigDecimal d = nz(ligneEcritureRepository.sumByCompteCodeAndDateBetween(
                compteCode, baseDebut, baseFin, SensLigne.DEBIT));
        BigDecimal c = nz(ligneEcritureRepository.sumByCompteCodeAndDateBetween(
                compteCode, baseDebut, baseFin, SensLigne.CREDIT));
        return d.subtract(c);
    }

    @Override
    public byte[] exportXlsx(GrandLivreFilterDto filter) {
        GrandLivreDto dto = compute(filter);

        // Aplatissement : 1 ligne par ligne d'ecriture + entete par compte
        List<FlatRow> rows = new ArrayList<>();
        for (CompteGrandLivreDto c : dto.comptes()) {
            rows.add(FlatRow.header(c));
            for (LigneGrandLivreDto l : c.lignes()) {
                rows.add(FlatRow.line(c.compteCode(), l));
            }
            rows.add(FlatRow.subtotal(c));
        }

        List<ColumnSpec<FlatRow>> columns = List.of(
                new ColumnSpec<>("Compte", ColumnType.TEXT, FlatRow::compteCode),
                new ColumnSpec<>("Libelle compte", ColumnType.TEXT, FlatRow::compteLibelle),
                new ColumnSpec<>("Date", ColumnType.DATE, FlatRow::date),
                new ColumnSpec<>("N. ecriture", ColumnType.TEXT, FlatRow::numero),
                new ColumnSpec<>("Journal", ColumnType.TEXT, FlatRow::journal),
                new ColumnSpec<>("Libelle", ColumnType.TEXT, FlatRow::libelle),
                new ColumnSpec<>("Reference", ColumnType.TEXT, FlatRow::reference),
                new ColumnSpec<>("Debit", ColumnType.MONEY, FlatRow::debit),
                new ColumnSpec<>("Credit", ColumnType.MONEY, FlatRow::credit),
                new ColumnSpec<>("Solde", ColumnType.MONEY, FlatRow::solde));

        return xlsxExportService.export("Grand Livre", columns, rows);
    }

    @Override
    public byte[] exportPdf(GrandLivreFilterDto filter) {
        GrandLivreDto dto = compute(filter);
        Document doc = EtatsPdfHelper.newLandscapeDocument();
        ByteArrayOutputStream out = EtatsPdfHelper.open(doc);
        try {
            String periode = "Periode : " + dto.dateDebut().format(EtatsPdfHelper.DATE_FMT)
                    + " - " + dto.dateFin().format(EtatsPdfHelper.DATE_FMT);
            EtatsPdfHelper.addHeader(doc, "Grand Livre", "", periode);

            float[] widths = {1.4f, 1.7f, 1.2f, 3.5f, 1.4f, 1.8f, 1.8f, 1.8f};

            for (CompteGrandLivreDto c : dto.comptes()) {
                EtatsPdfHelper.addParagraph(doc,
                        "Compte " + c.compteCode() + " - " + c.compteLibelle()
                                + "  (Report initial : " + EtatsPdfHelper.money(c.reportInitial()) + ")",
                        11f, true);

                PdfPTable table = EtatsPdfHelper.newTable(widths);
                table.setHeaderRows(1);
                table.addCell(EtatsPdfHelper.headerCell("Date"));
                table.addCell(EtatsPdfHelper.headerCell("N. ecriture"));
                table.addCell(EtatsPdfHelper.headerCell("Journal"));
                table.addCell(EtatsPdfHelper.headerCell("Libelle"));
                table.addCell(EtatsPdfHelper.headerCell("Reference"));
                table.addCell(EtatsPdfHelper.headerCell("Debit"));
                table.addCell(EtatsPdfHelper.headerCell("Credit"));
                table.addCell(EtatsPdfHelper.headerCell("Solde"));

                for (LigneGrandLivreDto l : c.lignes()) {
                    table.addCell(EtatsPdfHelper.textCell(
                            l.dateComptable() == null ? "" : l.dateComptable().format(EtatsPdfHelper.DATE_FMT)));
                    table.addCell(EtatsPdfHelper.textCell(l.numeroEcriture()));
                    table.addCell(EtatsPdfHelper.textCell(l.journalCode()));
                    table.addCell(EtatsPdfHelper.textCell(l.libelleEcriture()));
                    table.addCell(EtatsPdfHelper.textCell(l.reference()));
                    table.addCell(EtatsPdfHelper.moneyCell(l.debit()));
                    table.addCell(EtatsPdfHelper.moneyCell(l.credit()));
                    table.addCell(EtatsPdfHelper.moneyCell(l.soldeProgressif()));
                }
                // Sous-totaux compte
                table.addCell(EtatsPdfHelper.totalLabelCell("Sous-total"));
                table.addCell(EtatsPdfHelper.totalLabelCell(""));
                table.addCell(EtatsPdfHelper.totalLabelCell(""));
                table.addCell(EtatsPdfHelper.totalLabelCell(""));
                table.addCell(EtatsPdfHelper.totalLabelCell(""));
                table.addCell(EtatsPdfHelper.totalMoneyCell(c.totalDebit()));
                table.addCell(EtatsPdfHelper.totalMoneyCell(c.totalCredit()));
                table.addCell(EtatsPdfHelper.totalMoneyCell(c.soldeFinal()));

                EtatsPdfHelper.addTable(doc, table);
            }
            return EtatsPdfHelper.close(doc, out);
        } catch (RuntimeException e) {
            log.error("Grand Livre PDF export failed", e);
            if (doc.isOpen()) {
                doc.close();
            }
            throw e;
        }
    }

    private Resolved resolveAll(GrandLivreFilterDto filter) {
        LocalDate d1 = filter.dateDebut();
        LocalDate d2 = filter.dateFin();
        LocalDate exDebut = null;
        Optional<Exercice> exOpt = filter.exerciceId() != null
                ? exerciceRepository.findById(filter.exerciceId())
                : Optional.empty();
        if (filter.exerciceId() != null && exOpt.isEmpty()) {
            throw new ResourceNotFoundException("error.exercice.notFound");
        }
        if (exOpt.isPresent()) {
            exDebut = exOpt.get().getDateDebut();
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
        return new Resolved(d1, d2, exDebut);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private record Resolved(LocalDate dateDebut, LocalDate dateFin, LocalDate exerciceDateDebut) {
    }

    /** Ligne aplatie pour l'export XLSX (entete / ligne / sous-total). */
    private record FlatRow(String compteCode, String compteLibelle, LocalDate date,
                           String numero, String journal, String libelle, String reference,
                           BigDecimal debit, BigDecimal credit, BigDecimal solde) {
        static FlatRow header(CompteGrandLivreDto c) {
            return new FlatRow(c.compteCode(), c.compteLibelle(), null, null, null,
                    "Report initial", null, null, null, c.reportInitial());
        }
        static FlatRow line(String compteCode, LigneGrandLivreDto l) {
            return new FlatRow(compteCode, null, l.dateComptable(), l.numeroEcriture(),
                    l.journalCode(), l.libelleEcriture(), l.reference(),
                    l.debit(), l.credit(), l.soldeProgressif());
        }
        static FlatRow subtotal(CompteGrandLivreDto c) {
            return new FlatRow(c.compteCode(), null, null, null, null,
                    "Sous-total compte", null, c.totalDebit(), c.totalCredit(), c.soldeFinal());
        }
    }
}

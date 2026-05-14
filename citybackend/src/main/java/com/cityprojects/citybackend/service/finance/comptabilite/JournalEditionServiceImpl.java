package com.cityprojects.citybackend.service.finance.comptabilite;

import com.cityprojects.citybackend.common.tenant.RequireTenant;
import com.cityprojects.citybackend.dto.finance.comptabilite.EcritureJournalDto;
import com.cityprojects.citybackend.dto.finance.comptabilite.JournalEditionDto;
import com.cityprojects.citybackend.dto.finance.comptabilite.JournalFilterDto;
import com.cityprojects.citybackend.dto.finance.comptabilite.LigneJournalDto;
import com.cityprojects.citybackend.entity.finance.EcritureComptable;
import com.cityprojects.citybackend.entity.finance.JournalComptable;
import com.cityprojects.citybackend.entity.finance.LigneEcriture;
import com.cityprojects.citybackend.entity.finance.SensLigne;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.exception.ResourceNotFoundException;
import com.cityprojects.citybackend.repository.finance.EcritureComptableRepository;
import com.cityprojects.citybackend.repository.finance.JournalComptableRepository;
import com.cityprojects.citybackend.repository.finance.PlanComptableGeneralRepository;
import com.cityprojects.citybackend.service.finance.comptabilite.pdf.EtatsPdfHelper;
import com.cityprojects.citybackend.service.reporting.export.XlsxExportService;
import com.cityprojects.citybackend.service.reporting.export.XlsxExportService.ColumnSpec;
import com.cityprojects.citybackend.service.reporting.export.XlsxExportService.ColumnType;
import com.lowagie.text.Document;
import com.lowagie.text.pdf.PdfPTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
 * Implementation de {@link JournalEditionService} (B5).
 */
@Service
@RequireTenant
@Transactional(readOnly = true)
public class JournalEditionServiceImpl implements JournalEditionService {

    private static final Logger log = LoggerFactory.getLogger(JournalEditionServiceImpl.class);
    /** Plafond pour 1 edition de journal (defensif). */
    private static final int MAX_ECRITURES = 100_000;

    private final EcritureComptableRepository ecritureRepository;
    private final JournalComptableRepository journalRepository;
    private final PlanComptableGeneralRepository pcgRepository;
    private final XlsxExportService xlsxExportService;

    public JournalEditionServiceImpl(EcritureComptableRepository ecritureRepository,
                                      JournalComptableRepository journalRepository,
                                      PlanComptableGeneralRepository pcgRepository,
                                      XlsxExportService xlsxExportService) {
        this.ecritureRepository = ecritureRepository;
        this.journalRepository = journalRepository;
        this.pcgRepository = pcgRepository;
        this.xlsxExportService = xlsxExportService;
    }

    @Override
    public JournalEditionDto compute(JournalFilterDto filter) {
        if (filter == null) {
            throw new BusinessException("error.etat.filterRequired");
        }
        if (filter.journalId() == null) {
            throw new BusinessException("error.etat.journalIdRequired");
        }
        if (filter.dateDebut() == null || filter.dateFin() == null) {
            throw new BusinessException("error.etat.filterRequired");
        }
        if (filter.dateFin().isBefore(filter.dateDebut())) {
            throw new BusinessException("error.etat.dateRangeInvalide");
        }

        JournalComptable journal = journalRepository.findById(filter.journalId())
                .orElseThrow(() -> new ResourceNotFoundException("error.journal.notFound"));

        // Ordre chronologique strict puis par id ascendant pour stabilite
        Page<EcritureComptable> page = ecritureRepository.findByJournalIdAndDateBetween(
                filter.journalId(), filter.dateDebut(), filter.dateFin(),
                PageRequest.of(0, MAX_ECRITURES, Sort.by(Sort.Order.asc("dateComptable"),
                        Sort.Order.asc("id"))));

        List<EcritureJournalDto> ecritureDtos = new ArrayList<>((int) page.getNumberOfElements());
        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;

        for (EcritureComptable ec : page.getContent()) {
            BigDecimal totD = BigDecimal.ZERO;
            BigDecimal totC = BigDecimal.ZERO;
            List<LigneJournalDto> lignesDto = new ArrayList<>();
            // Tri stable : ordre puis id
            List<LigneEcriture> lignes = new ArrayList<>(ec.getLignes());
            lignes.sort((a, b) -> {
                int o = Integer.compare(a.getOrdre(), b.getOrdre());
                if (o != 0) {
                    return o;
                }
                return Long.compare(a.getId() == null ? 0L : a.getId(),
                        b.getId() == null ? 0L : b.getId());
            });
            for (LigneEcriture l : lignes) {
                BigDecimal d = (l.getSens() == SensLigne.DEBIT) ? l.getMontant() : BigDecimal.ZERO;
                BigDecimal c = (l.getSens() == SensLigne.CREDIT) ? l.getMontant() : BigDecimal.ZERO;
                totD = totD.add(d);
                totC = totC.add(c);
                String libelleCompte = pcgRepository.findByCompteCode(l.getCompteCode())
                        .map(p -> p.getLibelle())
                        .orElse(l.getCompteCode());
                lignesDto.add(new LigneJournalDto(
                        l.getCompteCode(),
                        libelleCompte,
                        d.setScale(2, RoundingMode.HALF_UP),
                        c.setScale(2, RoundingMode.HALF_UP)));
            }
            ecritureDtos.add(new EcritureJournalDto(
                    ec.getDateComptable(),
                    ec.getNumero(),
                    ec.getLibelle(),
                    ec.getReference(),
                    lignesDto,
                    totD.setScale(2, RoundingMode.HALF_UP),
                    totC.setScale(2, RoundingMode.HALF_UP)));
            totalDebit = totalDebit.add(totD);
            totalCredit = totalCredit.add(totC);
        }

        return new JournalEditionDto(
                journal.getId(),
                journal.getCode(),
                journal.getLibelle(),
                filter.dateDebut(),
                filter.dateFin(),
                ecritureDtos,
                totalDebit.setScale(2, RoundingMode.HALF_UP),
                totalCredit.setScale(2, RoundingMode.HALF_UP),
                Instant.now());
    }

    @Override
    public byte[] exportXlsx(JournalFilterDto filter) {
        JournalEditionDto dto = compute(filter);

        List<FlatRow> rows = new ArrayList<>();
        for (EcritureJournalDto ec : dto.ecritures()) {
            rows.add(FlatRow.header(ec));
            for (LigneJournalDto l : ec.lignes()) {
                rows.add(FlatRow.line(ec, l));
            }
        }

        List<ColumnSpec<FlatRow>> columns = List.of(
                new ColumnSpec<>("Date", ColumnType.DATE, FlatRow::date),
                new ColumnSpec<>("N. ecriture", ColumnType.TEXT, FlatRow::numero),
                new ColumnSpec<>("Libelle", ColumnType.TEXT, FlatRow::libelle),
                new ColumnSpec<>("Reference", ColumnType.TEXT, FlatRow::reference),
                new ColumnSpec<>("Compte", ColumnType.TEXT, FlatRow::compteCode),
                new ColumnSpec<>("Libelle compte", ColumnType.TEXT, FlatRow::compteLibelle),
                new ColumnSpec<>("Debit", ColumnType.MONEY, FlatRow::debit),
                new ColumnSpec<>("Credit", ColumnType.MONEY, FlatRow::credit));

        return xlsxExportService.export("Journal " + dto.journalCode(), columns, rows);
    }

    @Override
    public byte[] exportPdf(JournalFilterDto filter) {
        JournalEditionDto dto = compute(filter);
        Document doc = EtatsPdfHelper.newPortraitDocument();
        ByteArrayOutputStream out = EtatsPdfHelper.open(doc);
        try {
            String periode = "Periode : " + dto.dateDebut().format(EtatsPdfHelper.DATE_FMT)
                    + " - " + dto.dateFin().format(EtatsPdfHelper.DATE_FMT);
            EtatsPdfHelper.addHeader(doc, "Journal " + dto.journalCode() + " - " + dto.journalLibelle(),
                    "", periode);

            float[] widths = {1.4f, 1.7f, 4f, 1.6f, 1.6f, 1.6f};
            for (EcritureJournalDto ec : dto.ecritures()) {
                EtatsPdfHelper.addParagraph(doc,
                        ec.numero() + " - " + (ec.dateComptable() == null ? "" : ec.dateComptable().format(EtatsPdfHelper.DATE_FMT))
                                + " - " + (ec.libelle() == null ? "" : ec.libelle()),
                        10f, true);

                PdfPTable table = EtatsPdfHelper.newTable(widths);
                table.setHeaderRows(1);
                table.addCell(EtatsPdfHelper.headerCell("Compte"));
                table.addCell(EtatsPdfHelper.headerCell("Libelle compte"));
                table.addCell(EtatsPdfHelper.headerCell("Reference"));
                table.addCell(EtatsPdfHelper.headerCell(""));
                table.addCell(EtatsPdfHelper.headerCell("Debit"));
                table.addCell(EtatsPdfHelper.headerCell("Credit"));
                for (LigneJournalDto l : ec.lignes()) {
                    table.addCell(EtatsPdfHelper.textCell(l.compteCode()));
                    table.addCell(EtatsPdfHelper.textCell(l.compteLibelle()));
                    table.addCell(EtatsPdfHelper.textCell(ec.reference()));
                    table.addCell(EtatsPdfHelper.textCell(""));
                    table.addCell(EtatsPdfHelper.moneyCell(l.debit()));
                    table.addCell(EtatsPdfHelper.moneyCell(l.credit()));
                }
                table.addCell(EtatsPdfHelper.totalLabelCell("Sous-total"));
                table.addCell(EtatsPdfHelper.totalLabelCell(""));
                table.addCell(EtatsPdfHelper.totalLabelCell(""));
                table.addCell(EtatsPdfHelper.totalLabelCell(""));
                table.addCell(EtatsPdfHelper.totalMoneyCell(ec.totalDebitEcriture()));
                table.addCell(EtatsPdfHelper.totalMoneyCell(ec.totalCreditEcriture()));
                EtatsPdfHelper.addTable(doc, table);
            }

            // Totaux journal en bas
            PdfPTable totals = EtatsPdfHelper.newTable(new float[]{4f, 1.6f, 1.6f});
            totals.addCell(EtatsPdfHelper.totalLabelCell("TOTAL JOURNAL"));
            totals.addCell(EtatsPdfHelper.totalMoneyCell(dto.totalDebit()));
            totals.addCell(EtatsPdfHelper.totalMoneyCell(dto.totalCredit()));
            EtatsPdfHelper.addTable(doc, totals);

            return EtatsPdfHelper.close(doc, out);
        } catch (RuntimeException e) {
            log.error("Journal PDF export failed", e);
            if (doc.isOpen()) {
                doc.close();
            }
            throw e;
        }
    }

    private record FlatRow(LocalDate date, String numero, String libelle, String reference,
                           String compteCode, String compteLibelle,
                           BigDecimal debit, BigDecimal credit) {
        static FlatRow header(EcritureJournalDto ec) {
            return new FlatRow(ec.dateComptable(), ec.numero(), ec.libelle(), ec.reference(),
                    null, null, null, null);
        }
        static FlatRow line(EcritureJournalDto ec, LigneJournalDto l) {
            return new FlatRow(null, ec.numero(), null, null,
                    l.compteCode(), l.compteLibelle(), l.debit(), l.credit());
        }
    }
}

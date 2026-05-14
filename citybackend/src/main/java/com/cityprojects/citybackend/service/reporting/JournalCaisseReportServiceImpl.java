package com.cityprojects.citybackend.service.reporting;

import com.cityprojects.citybackend.common.tenant.RequireTenant;
import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.dto.reporting.JournalCaisseDto;
import com.cityprojects.citybackend.dto.reporting.JournalCaisseDto.ModePaiementLigneDto;
import com.cityprojects.citybackend.dto.reporting.projection.PaiementModeProjection;
import com.cityprojects.citybackend.entity.restaurant.Commande;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.repository.finance.PaiementRepository;
import com.cityprojects.citybackend.repository.restaurant.CommandeRepository;
import com.cityprojects.citybackend.service.reporting.export.PdfExportService;
import com.cityprojects.citybackend.service.reporting.export.XlsxExportService;
import com.cityprojects.citybackend.service.reporting.export.XlsxExportService.ColumnSpec;
import com.cityprojects.citybackend.service.reporting.export.XlsxExportService.ColumnType;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementation R-RES-001 — Journal de caisse (Tour 41 P2).
 */
@Service
@RequireTenant
@Transactional(readOnly = true)
public class JournalCaisseReportServiceImpl implements JournalCaisseReportService {

    private static final ZoneId NOUAKCHOTT = ZoneId.of("Africa/Nouakchott");

    private final CommandeRepository commandeRepository;
    private final PaiementRepository paiementRepository;
    private final PdfExportService pdfExportService;
    private final XlsxExportService xlsxExportService;

    public JournalCaisseReportServiceImpl(CommandeRepository commandeRepository,
                                          PaiementRepository paiementRepository,
                                          PdfExportService pdfExportService,
                                          XlsxExportService xlsxExportService) {
        this.commandeRepository = commandeRepository;
        this.paiementRepository = paiementRepository;
        this.pdfExportService = pdfExportService;
        this.xlsxExportService = xlsxExportService;
    }

    @Override
    @Cacheable(value = "journal-caisse",
            key = "T(com.cityprojects.citybackend.common.tenant.TenantContext).get() + '-' + #date")
    public JournalCaisseDto computeJournal(LocalDate date) {
        validate(date);

        Instant start = date.atStartOfDay(NOUAKCHOTT).toInstant();
        Instant end = date.plusDays(1).atStartOfDay(NOUAKCHOTT).toInstant();

        List<Commande> commandes = commandeRepository.findEncaisseesBetween(start, end);
        long nbCommandes = commandes.size();
        BigDecimal totalRecettes = BigDecimal.ZERO;
        for (Commande c : commandes) {
            totalRecettes = totalRecettes.add(nz(c.getMontantPaye()));
        }

        List<PaiementModeProjection> projections = paiementRepository.aggregateByModeOnDate(date);
        List<ModePaiementLigneDto> modes = new ArrayList<>(projections.size());
        for (PaiementModeProjection p : projections) {
            modes.add(new ModePaiementLigneDto(
                    p.getModePaiement(),
                    nz(p.getNbPaiements()),
                    nz(p.getMontantTotal())));
        }

        return new JournalCaisseDto(date, nbCommandes, totalRecettes, modes);
    }

    @Override
    public byte[] exportPdf(LocalDate date) {
        JournalCaisseDto dto = computeJournal(date);
        Map<String, Object> params = new HashMap<>();
        params.put("REPORT_TITLE", "Journal de caisse");
        params.put("HOTEL_ID", TenantContext.get());
        params.put("DATE", dto.date());
        params.put("NB_COMMANDES", dto.nbCommandes());
        params.put("TOTAL_RECETTES", dto.totalRecettes());
        return pdfExportService.exportToPdf("journal-caisse", params, dto.breakdownModes());
    }

    @Override
    public byte[] exportXlsx(LocalDate date) {
        JournalCaisseDto dto = computeJournal(date);
        List<ColumnSpec<ModePaiementLigneDto>> columns = List.of(
                new ColumnSpec<>("Mode", ColumnType.TEXT,
                        m -> m.modePaiement() == null ? "" : m.modePaiement().name()),
                new ColumnSpec<>("Nb paiements", ColumnType.INTEGER, ModePaiementLigneDto::nbPaiements),
                new ColumnSpec<>("Montant", ColumnType.MONEY, ModePaiementLigneDto::montantTotal));
        return xlsxExportService.export("Journal_Caisse", columns, dto.breakdownModes());
    }

    private static long nz(Long value) {
        return value == null ? 0L : value;
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static void validate(LocalDate date) {
        if (date == null) {
            throw new BusinessException("error.report.date.required");
        }
    }
}

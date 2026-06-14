package com.cityprojects.citybackend.service.reporting;

import com.cityprojects.citybackend.common.tenant.RequireTenant;
import com.cityprojects.citybackend.dto.reporting.JournalCaisseDto;
import com.cityprojects.citybackend.dto.reporting.JournalCaisseDto.ModePaiementLigneDto;
import com.cityprojects.citybackend.dto.reporting.projection.PaiementModeProjection;
import com.cityprojects.citybackend.entity.restaurant.Commande;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.repository.finance.PaiementRepository;
import com.cityprojects.citybackend.repository.restaurant.CommandeRepository;
import com.cityprojects.citybackend.service.reporting.export.DocumentExportService;
import com.cityprojects.citybackend.service.reporting.export.ReportDocument;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementation R-RES-001 — Journal de caisse.
 *
 * <p>Tour 51ter : exports unifiés via {@link DocumentExportService} avec
 * bordures partout.</p>
 */
@Service
@RequireTenant
@Transactional(readOnly = true)
public class JournalCaisseReportServiceImpl implements JournalCaisseReportService {

    private static final ZoneId NOUAKCHOTT = ZoneId.of("Africa/Nouakchott");

    private final CommandeRepository commandeRepository;
    private final PaiementRepository paiementRepository;
    private final DocumentExportService documentExportService;

    public JournalCaisseReportServiceImpl(CommandeRepository commandeRepository,
                                          PaiementRepository paiementRepository,
                                          DocumentExportService documentExportService) {
        this.commandeRepository = commandeRepository;
        this.paiementRepository = paiementRepository;
        this.documentExportService = documentExportService;
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
    public byte[] exportXlsx(LocalDate date) { return documentExportService.toXlsx(buildDocument(date)); }
    @Override
    public byte[] exportDocx(LocalDate date) { return documentExportService.toDocx(buildDocument(date)); }
    @Override
    public byte[] exportPdf(LocalDate date) { return documentExportService.toPdf(buildDocument(date)); }

    private ReportDocument buildDocument(LocalDate date) {
        JournalCaisseDto dto = computeJournal(date);
        String title = "Journal de caisse";
        String period = String.format("Date : %s", dto.date());

        List<ReportDocument.Kpi> kpis = List.of(
                new ReportDocument.Kpi("Nb commandes encaissées", String.valueOf(dto.nbCommandes())),
                new ReportDocument.Kpi("Total recettes", money(dto.totalRecettes()))
        );

        List<String> headers = List.of("Mode paiement", "Nb paiements", "Montant");
        List<List<Object>> rows = new ArrayList<>(dto.breakdownModes().size());
        for (ModePaiementLigneDto m : dto.breakdownModes()) {
            rows.add(List.of(
                    m.modePaiement() != null ? m.modePaiement().name() : "",
                    m.nbPaiements(),
                    money(m.montantTotal())
            ));
        }
        return new ReportDocument(title, period, kpis,
                new ReportDocument.Table("Répartition par mode de paiement", headers, rows));
    }

    private static String money(BigDecimal v) {
        if (v == null) return "0,00";
        return v.setScale(2, RoundingMode.HALF_UP).toPlainString();
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

package com.cityprojects.citybackend.service.reporting;

import com.cityprojects.citybackend.common.tenant.RequireTenant;
import com.cityprojects.citybackend.dto.reporting.CARecapDto;
import com.cityprojects.citybackend.dto.reporting.ReportPeriode;
import com.cityprojects.citybackend.dto.reporting.projection.CARecapProjection;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.repository.finance.FactureRepository;
import com.cityprojects.citybackend.repository.finance.PaiementRepository;
import com.cityprojects.citybackend.service.reporting.export.XlsxExportService;
import com.cityprojects.citybackend.service.reporting.export.XlsxExportService.ColumnSpec;
import com.cityprojects.citybackend.service.reporting.export.XlsxExportService.ColumnType;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Implementation R-FIN-001 (Tour 40 MVP).
 */
@Service
@RequireTenant
@Transactional(readOnly = true)
public class CARecapReportServiceImpl implements CARecapReportService {

    private static final String DEVISE = "MRU";

    private final FactureRepository factureRepository;
    private final PaiementRepository paiementRepository;
    private final XlsxExportService xlsxExportService;

    public CARecapReportServiceImpl(FactureRepository factureRepository,
                                    PaiementRepository paiementRepository,
                                    XlsxExportService xlsxExportService) {
        this.factureRepository = factureRepository;
        this.paiementRepository = paiementRepository;
        this.xlsxExportService = xlsxExportService;
    }

    @Override
    @Cacheable(value = "ca-recap",
            key = "T(com.cityprojects.citybackend.common.tenant.TenantContext).get() + '-' + #periode + '-' + #from + '-' + #to + '-' + #reference")
    public CARecapDto computeCA(ReportPeriode periode, LocalDate from, LocalDate to, LocalDate reference) {
        ReportPeriode.DateRange range = resolveRange(periode, from, to, reference);

        CARecapProjection agg = factureRepository.aggregateCaOnRange(range.from(), range.to());
        long nbPaiements = paiementRepository.countValidesOnRange(range.from(), range.to());
        BigDecimal montantEncaisse = paiementRepository.sumMontantValidesOnRange(range.from(), range.to());

        long nbFactures = (agg == null || agg.getNbFactures() == null) ? 0L : agg.getNbFactures();
        BigDecimal ht = nz(agg == null ? null : agg.getCaEmisHt());
        BigDecimal tva = nz(agg == null ? null : agg.getCaEmisTva());
        BigDecimal ttc = nz(agg == null ? null : agg.getCaEmisTtc());
        BigDecimal payeTtc = nz(agg == null ? null : agg.getCaPayeTtc());

        return new CARecapDto(
                range.from(), range.to(),
                nbFactures, ht, tva, ttc, payeTtc,
                nbPaiements, nz(montantEncaisse), DEVISE);
    }

    @Override
    public byte[] exportXlsx(ReportPeriode periode, LocalDate from, LocalDate to, LocalDate reference) {
        CARecapDto dto = computeCA(periode, from, to, reference);
        // Une seule ligne de donnees : les agregats.
        List<ColumnSpec<CARecapDto>> columns = List.of(
                new ColumnSpec<>("Du", ColumnType.DATE, CARecapDto::from),
                new ColumnSpec<>("Au (exclus)", ColumnType.DATE, CARecapDto::to),
                new ColumnSpec<>("Nb factures", ColumnType.INTEGER, CARecapDto::nbFactures),
                new ColumnSpec<>("CA HT", ColumnType.MONEY, CARecapDto::caEmisHt),
                new ColumnSpec<>("CA TVA", ColumnType.MONEY, CARecapDto::caEmisTva),
                new ColumnSpec<>("CA TTC", ColumnType.MONEY, CARecapDto::caEmisTtc),
                new ColumnSpec<>("Deja paye TTC", ColumnType.MONEY, CARecapDto::caPayeTtc),
                new ColumnSpec<>("Nb paiements valides", ColumnType.INTEGER, CARecapDto::nbPaiements),
                new ColumnSpec<>("Encaisse", ColumnType.MONEY, CARecapDto::montantEncaisse),
                new ColumnSpec<>("Devise", ColumnType.TEXT, CARecapDto::devise));
        return xlsxExportService.export("CA_Recap", columns, List.of(dto));
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static ReportPeriode.DateRange resolveRange(ReportPeriode periode, LocalDate from, LocalDate to, LocalDate reference) {
        if (periode == null) {
            throw new BusinessException("error.report.periode.missing");
        }
        if (periode == ReportPeriode.CUSTOM) {
            if (from == null || to == null) {
                throw new BusinessException("error.report.dateRange.required");
            }
            if (!from.isBefore(to)) {
                throw new BusinessException("error.report.dateRange.invalid");
            }
            return new ReportPeriode.DateRange(from, to);
        }
        LocalDate ref = reference != null ? reference : LocalDate.now();
        return periode.resolve(ref);
    }
}

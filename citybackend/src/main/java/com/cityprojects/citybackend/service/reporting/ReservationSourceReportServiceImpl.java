package com.cityprojects.citybackend.service.reporting;

import com.cityprojects.citybackend.common.tenant.RequireTenant;
import com.cityprojects.citybackend.dto.reporting.ReservationSourceDto;
import com.cityprojects.citybackend.dto.reporting.ReservationSourceDto.SourceBreakdownDto;
import com.cityprojects.citybackend.dto.reporting.projection.ReservationSourceProjection;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.repository.hebergement.ReservationRepository;
import com.cityprojects.citybackend.service.reporting.export.DocumentExportService;
import com.cityprojects.citybackend.service.reporting.export.ReportDocument;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementation R-HEB-004 — Source des reservations.
 *
 * <p>Tour 51ter : exports Excel / Word / PDF unifiés via
 * {@link DocumentExportService}. Toutes les tables ont des bordures.</p>
 *
 * <p>NULL {@code source_canal} consolidé sous la clé {@code "NON_RENSEIGNE"}.</p>
 */
@Service
@RequireTenant
@Transactional(readOnly = true)
public class ReservationSourceReportServiceImpl implements ReservationSourceReportService {

    public static final String NON_RENSEIGNE = "NON_RENSEIGNE";

    private final ReservationRepository reservationRepository;
    private final DocumentExportService documentExportService;

    public ReservationSourceReportServiceImpl(ReservationRepository reservationRepository,
                                              DocumentExportService documentExportService) {
        this.reservationRepository = reservationRepository;
        this.documentExportService = documentExportService;
    }

    @Override
    @Cacheable(value = "reservation-sources",
            key = "T(com.cityprojects.citybackend.common.tenant.TenantContext).get() + '-' + #from + '-' + #to")
    public ReservationSourceDto computeBySource(LocalDate from, LocalDate to) {
        validate(from, to);

        List<ReservationSourceProjection> projections = reservationRepository
                .aggregateBySourceCanal(from, to);
        BigDecimal caTotal = nz(reservationRepository.sumMontantTotalOnRange(from, to));

        long totalReservations = 0L;
        for (ReservationSourceProjection p : projections) {
            totalReservations += nz(p.getNbReservations());
        }

        List<SourceBreakdownDto> breakdown = new ArrayList<>(projections.size());
        for (ReservationSourceProjection p : projections) {
            String canal = p.getSourceCanal() != null ? p.getSourceCanal() : NON_RENSEIGNE;
            long nb = nz(p.getNbReservations());
            BigDecimal pct = totalReservations <= 0L
                    ? BigDecimal.ZERO.setScale(2)
                    : BigDecimal.valueOf(nb * 100.0 / totalReservations)
                            .setScale(2, RoundingMode.HALF_UP);
            breakdown.add(new SourceBreakdownDto(canal, nb, nz(p.getCaMontant()), pct));
        }

        return new ReservationSourceDto(from, to, totalReservations, caTotal, breakdown);
    }

    @Override
    public byte[] exportXlsx(LocalDate from, LocalDate to) {
        return documentExportService.toXlsx(buildDocument(from, to));
    }

    @Override
    public byte[] exportDocx(LocalDate from, LocalDate to) {
        return documentExportService.toDocx(buildDocument(from, to));
    }

    @Override
    public byte[] exportPdf(LocalDate from, LocalDate to) {
        return documentExportService.toPdf(buildDocument(from, to));
    }

    /**
     * Construit le document pivot pour les 3 formats. Le breakdown est trié
     * par CA décroissant pour l'affichage.
     */
    private ReportDocument buildDocument(LocalDate from, LocalDate to) {
        ReservationSourceDto dto = computeBySource(from, to);

        String title = "Sources des réservations";
        String period = String.format("Période : %s → %s", from, to);

        List<ReportDocument.Kpi> kpis = List.of(
                new ReportDocument.Kpi("Total réservations", String.valueOf(dto.totalReservations())),
                new ReportDocument.Kpi("CA total (MRU)", formatMoney(dto.caTotal()))
        );

        List<String> headers = List.of("Canal", "Nb réservations", "CA (MRU)", "% du total");
        List<List<Object>> rows = new ArrayList<>(dto.breakdown().size());
        for (SourceBreakdownDto b : dto.breakdown()) {
            rows.add(List.of(
                    b.sourceCanal() != null ? b.sourceCanal() : "",
                    b.nbReservations(),
                    formatMoney(b.caMontant()),
                    formatPercentage(b.pourcentage())
            ));
        }

        ReportDocument.Table table = new ReportDocument.Table(
                "Détail par canal", headers, rows);
        return new ReportDocument(title, period, kpis, table);
    }

    private static String formatMoney(BigDecimal value) {
        if (value == null) return "0,00";
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String formatPercentage(BigDecimal value) {
        if (value == null) return "0,00 %";
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString() + " %";
    }

    private static long nz(Long value) {
        return value == null ? 0L : value;
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
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

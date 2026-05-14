package com.cityprojects.citybackend.service.reporting;

import com.cityprojects.citybackend.common.tenant.RequireTenant;
import com.cityprojects.citybackend.dto.reporting.ReservationSourceDto;
import com.cityprojects.citybackend.dto.reporting.ReservationSourceDto.SourceBreakdownDto;
import com.cityprojects.citybackend.dto.reporting.projection.ReservationSourceProjection;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.repository.hebergement.ReservationRepository;
import com.cityprojects.citybackend.service.reporting.export.XlsxExportService;
import com.cityprojects.citybackend.service.reporting.export.XlsxExportService.ColumnSpec;
import com.cityprojects.citybackend.service.reporting.export.XlsxExportService.ColumnType;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementation R-HEB-004 — Source des reservations (Tour 41 P1).
 *
 * <p>NULL {@code source_canal} est consolide sous la cle {@code "NON_RENSEIGNE"}
 * (compatibilite legacy : reservations Tours 8-40 sans canal).</p>
 */
@Service
@RequireTenant
@Transactional(readOnly = true)
public class ReservationSourceReportServiceImpl implements ReservationSourceReportService {

    /** Cle d'affichage pour les canaux non renseignes (NULL en BDD). */
    public static final String NON_RENSEIGNE = "NON_RENSEIGNE";

    private final ReservationRepository reservationRepository;
    private final XlsxExportService xlsxExportService;

    public ReservationSourceReportServiceImpl(ReservationRepository reservationRepository,
                                              XlsxExportService xlsxExportService) {
        this.reservationRepository = reservationRepository;
        this.xlsxExportService = xlsxExportService;
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
        ReservationSourceDto dto = computeBySource(from, to);
        List<ColumnSpec<SourceBreakdownDto>> columns = List.of(
                new ColumnSpec<>("Canal", ColumnType.TEXT, SourceBreakdownDto::sourceCanal),
                new ColumnSpec<>("Nb reservations", ColumnType.INTEGER, SourceBreakdownDto::nbReservations),
                new ColumnSpec<>("CA", ColumnType.MONEY, SourceBreakdownDto::caMontant),
                new ColumnSpec<>("Pourcentage", ColumnType.DECIMAL, SourceBreakdownDto::pourcentage));
        return xlsxExportService.export("Sources_Reservations", columns, dto.breakdown());
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

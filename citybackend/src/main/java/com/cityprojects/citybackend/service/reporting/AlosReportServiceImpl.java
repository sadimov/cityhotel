package com.cityprojects.citybackend.service.reporting;

import com.cityprojects.citybackend.common.tenant.RequireTenant;
import com.cityprojects.citybackend.dto.reporting.AlosDto;
import com.cityprojects.citybackend.dto.reporting.AlosDto.AlosBreakdownDto;
import com.cityprojects.citybackend.dto.reporting.AlosDto.AlosGroupBy;
import com.cityprojects.citybackend.dto.reporting.projection.AlosByTypeProjection;
import com.cityprojects.citybackend.entity.hebergement.Reservation;
import com.cityprojects.citybackend.entity.hebergement.StatutReservation;
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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementation R-HEB-002 — ALOS (Tour 41 P1).
 *
 * <p>Read-only : agrege sur {@code hebergement.reservations}. Hibernate filtre
 * automatiquement par {@code hotel_id} via {@code @TenantId}.</p>
 */
@Service
@RequireTenant
@Transactional(readOnly = true)
public class AlosReportServiceImpl implements AlosReportService {

    private static final DateTimeFormatter MOIS_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final ReservationRepository reservationRepository;
    private final XlsxExportService xlsxExportService;

    public AlosReportServiceImpl(ReservationRepository reservationRepository,
                                 XlsxExportService xlsxExportService) {
        this.reservationRepository = reservationRepository;
        this.xlsxExportService = xlsxExportService;
    }

    @Override
    @Cacheable(value = "alos",
            key = "T(com.cityprojects.citybackend.common.tenant.TenantContext).get() + '-' + #from + '-' + #to + '-' + #groupBy")
    public AlosDto computeAlos(LocalDate from, LocalDate to, AlosGroupBy groupBy) {
        validate(from, to, groupBy);

        Object[] globalAgg = reservationRepository.aggregateAlosGlobal(from, to);
        long nbReservations = toLong(globalAgg, 0);
        long totalNuits = toLong(globalAgg, 1);
        BigDecimal alosGlobal = computeMean(totalNuits, nbReservations);

        List<AlosBreakdownDto> breakdown = switch (groupBy) {
            case TYPE_CHAMBRE -> breakdownByType(from, to);
            case MOIS -> breakdownByMonth(from, to);
        };

        return new AlosDto(from, to, groupBy, nbReservations, totalNuits, alosGlobal, breakdown);
    }

    @Override
    public byte[] exportXlsx(LocalDate from, LocalDate to, AlosGroupBy groupBy) {
        AlosDto dto = computeAlos(from, to, groupBy);
        List<ColumnSpec<AlosBreakdownDto>> columns = List.of(
                new ColumnSpec<>("Dimension", ColumnType.TEXT, AlosBreakdownDto::dimensionLabel),
                new ColumnSpec<>("Nb reservations", ColumnType.INTEGER, AlosBreakdownDto::nbReservations),
                new ColumnSpec<>("Total nuits", ColumnType.INTEGER, AlosBreakdownDto::totalNuits),
                new ColumnSpec<>("ALOS", ColumnType.DECIMAL, AlosBreakdownDto::alos));
        return xlsxExportService.export("ALOS", columns, dto.breakdown());
    }

    private List<AlosBreakdownDto> breakdownByType(LocalDate from, LocalDate to) {
        List<AlosByTypeProjection> projections = reservationRepository.aggregateAlosByType(from, to);
        List<AlosBreakdownDto> result = new ArrayList<>(projections.size());
        for (AlosByTypeProjection p : projections) {
            long nb = nz(p.getNbReservations());
            long nuits = nz(p.getTotalNuits());
            result.add(new AlosBreakdownDto(
                    p.getTypeCode(),
                    p.getTypeNom() != null ? p.getTypeNom() : p.getTypeCode(),
                    nb, nuits, computeMean(nuits, nb)));
        }
        return result;
    }

    private List<AlosBreakdownDto> breakdownByMonth(LocalDate from, LocalDate to) {
        List<Reservation> reservations = reservationRepository.findAllArrivantBetween(from, to);
        Map<String, long[]> byMonth = new LinkedHashMap<>();
        for (Reservation r : reservations) {
            if (r.getStatut() == StatutReservation.ANNULEE
                    || r.getStatut() == StatutReservation.NO_SHOW) {
                continue;
            }
            String key = r.getDateArrivee().format(MOIS_FMT);
            long[] agg = byMonth.computeIfAbsent(key, k -> new long[]{0L, 0L});
            agg[0]++;
            agg[1] += r.getNbNuits() == null ? 0L : r.getNbNuits();
        }
        List<AlosBreakdownDto> result = new ArrayList<>(byMonth.size());
        byMonth.forEach((key, agg) -> result.add(new AlosBreakdownDto(
                key, key, agg[0], agg[1], computeMean(agg[1], agg[0]))));
        return result;
    }

    private static BigDecimal computeMean(long total, long count) {
        if (count <= 0L) {
            return BigDecimal.ZERO.setScale(2);
        }
        return BigDecimal.valueOf(total)
                .divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
    }

    private static long nz(Long value) {
        return value == null ? 0L : value;
    }

    private static long toLong(Object[] row, int index) {
        if (row == null || row.length <= index || row[index] == null) {
            return 0L;
        }
        Object v = row[index];
        if (v instanceof Number n) {
            return n.longValue();
        }
        return 0L;
    }

    private static void validate(LocalDate from, LocalDate to, AlosGroupBy groupBy) {
        if (from == null || to == null) {
            throw new BusinessException("error.report.dateRange.required");
        }
        if (!from.isBefore(to)) {
            throw new BusinessException("error.report.dateRange.invalid");
        }
        if (groupBy == null) {
            throw new BusinessException("error.report.groupBy.required");
        }
    }
}

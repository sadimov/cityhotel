package com.cityprojects.citybackend.service.reporting;

import com.cityprojects.citybackend.common.tenant.RequireTenant;
import com.cityprojects.citybackend.dto.reporting.NoShowRateDto;
import com.cityprojects.citybackend.dto.reporting.NoShowRateDto.NoShowBreakdownDto;
import com.cityprojects.citybackend.dto.reporting.NoShowRateDto.NoShowGroupBy;
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
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Implementation R-HEB-003 — Taux de no-show (Tour 41 P1).
 */
@Service
@RequireTenant
@Transactional(readOnly = true)
public class NoShowRateReportServiceImpl implements NoShowRateReportService {

    private static final DateTimeFormatter JOUR_FMT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter MOIS_FMT = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final WeekFields WEEK_FIELDS = WeekFields.of(DayOfWeek.MONDAY, 4);

    private final ReservationRepository reservationRepository;
    private final XlsxExportService xlsxExportService;

    public NoShowRateReportServiceImpl(ReservationRepository reservationRepository,
                                       XlsxExportService xlsxExportService) {
        this.reservationRepository = reservationRepository;
        this.xlsxExportService = xlsxExportService;
    }

    @Override
    @Cacheable(value = "no-show-rate",
            key = "T(com.cityprojects.citybackend.common.tenant.TenantContext).get() + '-' + #from + '-' + #to + '-' + #groupBy")
    public NoShowRateDto computeNoShowRate(LocalDate from, LocalDate to, NoShowGroupBy groupBy) {
        validate(from, to, groupBy);

        Object[] global = reservationRepository.aggregateNoShowGlobal(from, to);
        long total = toLong(global, 0);
        long noShow = toLong(global, 1);
        BigDecimal tauxGlobal = ratePercent(noShow, total);

        List<Reservation> reservations = reservationRepository.findAllArrivantBetween(from, to);
        List<NoShowBreakdownDto> breakdown = buildBreakdown(reservations, groupBy);

        return new NoShowRateDto(from, to, groupBy, total, noShow, tauxGlobal, breakdown);
    }

    @Override
    public byte[] exportXlsx(LocalDate from, LocalDate to, NoShowGroupBy groupBy) {
        NoShowRateDto dto = computeNoShowRate(from, to, groupBy);
        List<ColumnSpec<NoShowBreakdownDto>> columns = List.of(
                new ColumnSpec<>("Periode", ColumnType.TEXT, NoShowBreakdownDto::dimensionLabel),
                new ColumnSpec<>("Total reservations", ColumnType.INTEGER, NoShowBreakdownDto::totalReservations),
                new ColumnSpec<>("Nb no-show", ColumnType.INTEGER, NoShowBreakdownDto::nbNoShow),
                new ColumnSpec<>("Taux %", ColumnType.DECIMAL, NoShowBreakdownDto::taux));
        return xlsxExportService.export("No_Show_Rate", columns, dto.breakdown());
    }

    private List<NoShowBreakdownDto> buildBreakdown(List<Reservation> reservations, NoShowGroupBy groupBy) {
        Map<String, long[]> agg = new LinkedHashMap<>();
        for (Reservation r : reservations) {
            String key = bucketKey(r.getDateArrivee(), groupBy);
            long[] counts = agg.computeIfAbsent(key, k -> new long[]{0L, 0L});
            counts[0]++;
            if (r.getStatut() == StatutReservation.NO_SHOW) {
                counts[1]++;
            }
        }
        List<NoShowBreakdownDto> result = new ArrayList<>(agg.size());
        agg.forEach((key, counts) -> result.add(new NoShowBreakdownDto(
                key, key, counts[0], counts[1], ratePercent(counts[1], counts[0]))));
        return result;
    }

    private static String bucketKey(LocalDate date, NoShowGroupBy groupBy) {
        return switch (groupBy) {
            case JOUR -> date.format(JOUR_FMT);
            case SEMAINE -> {
                int week = date.get(WEEK_FIELDS.weekOfWeekBasedYear());
                int year = date.get(WEEK_FIELDS.weekBasedYear());
                yield String.format(Locale.ROOT, "%04d-W%02d", year, week);
            }
            case MOIS -> date.format(MOIS_FMT);
        };
    }

    private static BigDecimal ratePercent(long numerator, long denominator) {
        if (denominator <= 0L) {
            return BigDecimal.ZERO.setScale(2);
        }
        return BigDecimal.valueOf(numerator * 100.0 / denominator)
                .setScale(2, RoundingMode.HALF_UP);
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

    private static void validate(LocalDate from, LocalDate to, NoShowGroupBy groupBy) {
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

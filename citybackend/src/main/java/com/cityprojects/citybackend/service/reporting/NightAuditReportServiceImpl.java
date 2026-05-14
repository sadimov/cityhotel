package com.cityprojects.citybackend.service.reporting;

import com.cityprojects.citybackend.common.tenant.RequireTenant;
import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.dto.reporting.NightAuditRecapDto;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.repository.hebergement.NuiteeRepository;
import com.cityprojects.citybackend.repository.hebergement.ReservationRepository;
import com.cityprojects.citybackend.service.reporting.export.PdfExportService;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementation R-NA-001 (Tour 40 MVP).
 *
 * <p>Iterate jour par jour ; chaque jour declenche 4 requetes (legeres) sur
 * {@code Reservation} et {@code Nuitee}. Pour des plages > 30 jours, le cache
 * @Cacheable (en V1.1) reduira la charge.</p>
 */
@Service
@RequireTenant
@Transactional(readOnly = true)
public class NightAuditReportServiceImpl implements NightAuditReportService {

    /** Garde-fou : on refuse les plages absurdes pour eviter d'iterer N x 4 requetes inutiles. */
    static final long MAX_RANGE_DAYS = 366L;

    private final ReservationRepository reservationRepository;
    private final NuiteeRepository nuiteeRepository;
    private final PdfExportService pdfExportService;

    public NightAuditReportServiceImpl(ReservationRepository reservationRepository,
                                       NuiteeRepository nuiteeRepository,
                                       PdfExportService pdfExportService) {
        this.reservationRepository = reservationRepository;
        this.nuiteeRepository = nuiteeRepository;
        this.pdfExportService = pdfExportService;
    }

    @Override
    @Cacheable(value = "night-audit",
            key = "T(com.cityprojects.citybackend.common.tenant.TenantContext).get() + '-' + #from + '-' + #to")
    public List<NightAuditRecapDto> computeRecap(LocalDate from, LocalDate to) {
        validateRange(from, to);
        long nbJours = ChronoUnit.DAYS.between(from, to);
        List<NightAuditRecapDto> result = new ArrayList<>((int) nbJours);
        LocalDate cursor = from;
        while (cursor.isBefore(to)) {
            long actives = reservationRepository.countActivesAtDate(cursor);
            long noShow = reservationRepository.countNoShowOnDate(cursor);
            long nuiteesGenerees = nuiteeRepository.countByDateNuit(cursor);
            long nuiteesConsommees = nuiteeRepository.countConsommeesByDate(cursor);
            long ecarts = actives - (nuiteesGenerees + noShow);
            result.add(new NightAuditRecapDto(cursor, actives, noShow, nuiteesGenerees, nuiteesConsommees, ecarts));
            cursor = cursor.plusDays(1);
        }
        return result;
    }

    @Override
    public byte[] exportPdf(LocalDate from, LocalDate to) {
        List<NightAuditRecapDto> data = computeRecap(from, to);
        Map<String, Object> params = new HashMap<>();
        params.put("REPORT_TITLE", "Recap night audit");
        params.put("HOTEL_ID", TenantContext.get());
        params.put("DATE_FROM", from);
        params.put("DATE_TO", to);
        params.put("NB_LIGNES", data.size());
        return pdfExportService.exportToPdf("night-audit", params, data);
    }

    private static void validateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new BusinessException("error.report.dateRange.required");
        }
        if (!from.isBefore(to)) {
            throw new BusinessException("error.report.dateRange.invalid");
        }
        if (ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) {
            throw new BusinessException("error.report.dateRange.tooLarge");
        }
    }
}

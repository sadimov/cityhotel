package com.cityprojects.citybackend.service.reporting;

import com.cityprojects.citybackend.common.tenant.RequireTenant;
import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.dto.reporting.KpiReceptionDto;
import com.cityprojects.citybackend.entity.hebergement.Reservation;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.repository.hebergement.ChambreRepository;
import com.cityprojects.citybackend.repository.hebergement.NuiteeRepository;
import com.cityprojects.citybackend.repository.hebergement.ReservationRepository;
import com.cityprojects.citybackend.service.reporting.export.PdfExportService;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementation R-HEB-005 — KPIs reception jour (Tour 41 P1).
 */
@Service
@RequireTenant
@Transactional(readOnly = true)
public class KpiReceptionReportServiceImpl implements KpiReceptionReportService {

    private static final ZoneId NOUAKCHOTT = ZoneId.of("Africa/Nouakchott");

    private final ReservationRepository reservationRepository;
    private final ChambreRepository chambreRepository;
    private final NuiteeRepository nuiteeRepository;
    private final PdfExportService pdfExportService;

    public KpiReceptionReportServiceImpl(ReservationRepository reservationRepository,
                                         ChambreRepository chambreRepository,
                                         NuiteeRepository nuiteeRepository,
                                         PdfExportService pdfExportService) {
        this.reservationRepository = reservationRepository;
        this.chambreRepository = chambreRepository;
        this.nuiteeRepository = nuiteeRepository;
        this.pdfExportService = pdfExportService;
    }

    @Override
    @Cacheable(value = "kpi-reception",
            key = "T(com.cityprojects.citybackend.common.tenant.TenantContext).get() + '-' + #date")
    public KpiReceptionDto computeKpis(LocalDate date) {
        validate(date);

        long checkIn = reservationRepository.countCheckInOnDate(date);
        long checkOut = reservationRepository.countCheckOutOnDate(date);
        long noShow = reservationRepository.countNoShowOnDate(date);
        long actives = reservationRepository.countActivesAtDate(date);
        long totalChambres = chambreRepository.countByActifTrue();
        long occupees = nuiteeRepository.countOccupeesOnRange(date, date.plusDays(1));

        // Walk-in : reservations dont la date d'arrivee = date ET createdAt = ce jour
        // (creation et arrivee dans la meme journee locale Nouakchott).
        List<Reservation> arrivees = reservationRepository.findArriveesOnDate(date);
        long walkIn = arrivees.stream()
                .filter(r -> r.getCreatedAt() != null
                        && r.getCreatedAt().atZone(NOUAKCHOTT).toLocalDate().equals(date))
                .count();

        BigDecimal taux = totalChambres <= 0L
                ? BigDecimal.ZERO.setScale(2)
                : BigDecimal.valueOf(occupees * 100.0 / totalChambres)
                        .setScale(2, RoundingMode.HALF_UP);

        return new KpiReceptionDto(date, checkIn, checkOut, walkIn, actives, noShow,
                totalChambres, occupees, taux);
    }

    @Override
    public byte[] exportPdf(LocalDate date) {
        KpiReceptionDto dto = computeKpis(date);
        Map<String, Object> params = new HashMap<>();
        params.put("REPORT_TITLE", "KPIs reception");
        params.put("HOTEL_ID", TenantContext.get());
        params.put("DATE", dto.date());
        params.put("NB_CHECK_IN", dto.nbCheckIn());
        params.put("NB_CHECK_OUT", dto.nbCheckOut());
        params.put("NB_WALK_IN", dto.nbWalkIn());
        params.put("NB_ACTIVES", dto.nbReservationsActives());
        params.put("NB_NO_SHOW", dto.nbNoShow());
        params.put("TOTAL_CHAMBRES", dto.totalChambres());
        params.put("NB_OCCUPEES", dto.nbChambresOccupees());
        params.put("TAUX_OCCUPATION", dto.tauxOccupationJour());
        return pdfExportService.exportToPdf("kpi-reception", params, List.of(dto));
    }

    private static void validate(LocalDate date) {
        if (date == null) {
            throw new BusinessException("error.report.date.required");
        }
    }
}

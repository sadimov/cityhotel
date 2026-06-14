package com.cityprojects.citybackend.service.reporting;

import com.cityprojects.citybackend.common.tenant.RequireTenant;
import com.cityprojects.citybackend.dto.reporting.KpiReceptionDto;
import com.cityprojects.citybackend.entity.hebergement.Reservation;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.repository.hebergement.ChambreRepository;
import com.cityprojects.citybackend.repository.hebergement.NuiteeRepository;
import com.cityprojects.citybackend.repository.hebergement.ReservationRepository;
import com.cityprojects.citybackend.service.reporting.export.DocumentExportService;
import com.cityprojects.citybackend.service.reporting.export.ReportDocument;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

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
    private final DocumentExportService documentExportService;

    public KpiReceptionReportServiceImpl(ReservationRepository reservationRepository,
                                         ChambreRepository chambreRepository,
                                         NuiteeRepository nuiteeRepository,
                                         DocumentExportService documentExportService) {
        this.reservationRepository = reservationRepository;
        this.chambreRepository = chambreRepository;
        this.nuiteeRepository = nuiteeRepository;
        this.documentExportService = documentExportService;
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
    public byte[] exportXlsx(LocalDate date) {
        return documentExportService.toXlsx(buildDocument(date));
    }

    @Override
    public byte[] exportDocx(LocalDate date) {
        return documentExportService.toDocx(buildDocument(date));
    }

    @Override
    public byte[] exportPdf(LocalDate date) {
        return documentExportService.toPdf(buildDocument(date));
    }

    /**
     * Rapport KPI sans détail tabulaire — uniquement des indicateurs scalaires.
     * Le tableau récap regroupe tous les KPIs en 2 colonnes (label/valeur) avec
     * bordures pour rester cohérent avec les autres rapports.
     */
    private ReportDocument buildDocument(LocalDate date) {
        KpiReceptionDto dto = computeKpis(date);
        String title = "KPIs Réception";
        String period = String.format("Date : %s", dto.date());

        List<ReportDocument.Kpi> kpis = List.of(
                new ReportDocument.Kpi("Check-in", String.valueOf(dto.nbCheckIn())),
                new ReportDocument.Kpi("Check-out", String.valueOf(dto.nbCheckOut())),
                new ReportDocument.Kpi("Walk-in", String.valueOf(dto.nbWalkIn())),
                new ReportDocument.Kpi("No-show", String.valueOf(dto.nbNoShow())),
                new ReportDocument.Kpi("Réservations actives", String.valueOf(dto.nbReservationsActives())),
                new ReportDocument.Kpi("Chambres actives", String.valueOf(dto.totalChambres())),
                new ReportDocument.Kpi("Chambres occupées", String.valueOf(dto.nbChambresOccupees())),
                new ReportDocument.Kpi("Taux d'occupation",
                        (dto.tauxOccupationJour() != null ? dto.tauxOccupationJour().toPlainString() : "0.00") + " %")
        );

        // Table récap : reprend les KPIs en format colonne pour cohérence visuelle
        List<String> headers = List.of("Indicateur", "Valeur");
        List<List<Object>> rows = new java.util.ArrayList<>(kpis.size());
        for (ReportDocument.Kpi k : kpis) {
            rows.add(List.of(k.label(), k.value()));
        }
        return new ReportDocument(title, period, kpis,
                new ReportDocument.Table("Récapitulatif", headers, rows));
    }

    private static void validate(LocalDate date) {
        if (date == null) {
            throw new BusinessException("error.report.date.required");
        }
    }
}

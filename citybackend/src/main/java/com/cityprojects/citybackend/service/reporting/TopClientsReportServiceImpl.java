package com.cityprojects.citybackend.service.reporting;

import com.cityprojects.citybackend.common.tenant.RequireTenant;
import com.cityprojects.citybackend.dto.reporting.TopClientDto;
import com.cityprojects.citybackend.dto.reporting.projection.TopClientProjection;
import com.cityprojects.citybackend.entity.hebergement.Reservation;
import com.cityprojects.citybackend.entity.hebergement.StatutReservation;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.repository.finance.FactureRepository;
import com.cityprojects.citybackend.repository.hebergement.ReservationRepository;
import com.cityprojects.citybackend.service.reporting.export.XlsxExportService;
import com.cityprojects.citybackend.service.reporting.export.XlsxExportService.ColumnSpec;
import com.cityprojects.citybackend.service.reporting.export.XlsxExportService.ColumnType;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementation R-CLI-001 (Tour 40 MVP).
 *
 * <p>Le tri (par CA decroissant) et la limite sont appliques cote SQL via
 * {@link FactureRepository#findTopClientsByPeriode}. Le nombre de nuitees est
 * calcule en sus en comptant les nuitees rattachees aux reservations du client
 * (tres petite quantite d'appels — un seul par top, somme cote service pour
 * eviter une jointure 4 tables).</p>
 */
@Service
@RequireTenant
@Transactional(readOnly = true)
public class TopClientsReportServiceImpl implements TopClientsReportService {

    static final int MAX_LIMIT = 100;

    private final FactureRepository factureRepository;
    private final ReservationRepository reservationRepository;
    private final XlsxExportService xlsxExportService;

    public TopClientsReportServiceImpl(FactureRepository factureRepository,
                                       ReservationRepository reservationRepository,
                                       XlsxExportService xlsxExportService) {
        this.factureRepository = factureRepository;
        this.reservationRepository = reservationRepository;
        this.xlsxExportService = xlsxExportService;
    }

    @Override
    @Cacheable(value = "top-clients",
            key = "T(com.cityprojects.citybackend.common.tenant.TenantContext).get() + '-' + #from + '-' + #to + '-' + #limit")
    public List<TopClientDto> findTopClients(LocalDate from, LocalDate to, int limit) {
        validate(from, to, limit);
        Pageable pageable = PageRequest.of(0, limit);
        List<TopClientProjection> projections = factureRepository
                .findTopClientsByPeriode(from, to, pageable);

        List<TopClientDto> result = new ArrayList<>(projections.size());
        int rang = 1;
        for (TopClientProjection p : projections) {
            long nbNuitees = countNuiteesForClient(p.getClientId(), from, to);
            result.add(new TopClientDto(
                    rang++,
                    p.getClientId(),
                    p.getNumeroClient(),
                    p.getNom(),
                    p.getPrenom(),
                    nbNuitees,
                    nz(p.getNbFactures()),
                    nz(p.getCaTtc()),
                    nz(p.getCaPaye())));
        }
        return result;
    }

    @Override
    public byte[] exportXlsx(LocalDate from, LocalDate to, int limit) {
        List<TopClientDto> data = findTopClients(from, to, limit);
        List<ColumnSpec<TopClientDto>> columns = List.of(
                new ColumnSpec<>("Rang", ColumnType.INTEGER, TopClientDto::rang),
                new ColumnSpec<>("Numero", ColumnType.TEXT, TopClientDto::numeroClient),
                new ColumnSpec<>("Nom", ColumnType.TEXT, TopClientDto::nom),
                new ColumnSpec<>("Prenom", ColumnType.TEXT, TopClientDto::prenom),
                new ColumnSpec<>("Nb nuitees", ColumnType.INTEGER, TopClientDto::nbNuitees),
                new ColumnSpec<>("Nb factures", ColumnType.INTEGER, TopClientDto::nbFactures),
                new ColumnSpec<>("CA TTC", ColumnType.MONEY, TopClientDto::caTtc),
                new ColumnSpec<>("CA paye", ColumnType.MONEY, TopClientDto::caPaye));
        return xlsxExportService.export("Top_Clients", columns, data);
    }

    /**
     * Pour eviter une jointure 4 tables ({@code Facture} - {@code Reservation} -
     * {@code Nuitee} - {@code Client}), on compte les nuitees des reservations
     * du client sur la plage. C'est une approximation : un client peut avoir des
     * nuitees "FACTUREE" sur des reservations dont la dateArrivee est anterieure
     * a la plage. Acceptable pour MVP, l'optimisation suivra en P1.
     */
    private long countNuiteesForClient(Long clientId, LocalDate from, LocalDate to) {
        // Approche simple : iterer les reservations du client et compter
        // celles dont la periode chevauche la plage. Limite la complexite.
        // Hibernate filtre auto par hotel_id via @TenantId.
        org.springframework.data.domain.Page<Reservation> page = reservationRepository
                .findByClientPrincipalIdOrderByDateArriveeDesc(clientId, PageRequest.of(0, 200));
        long total = 0L;
        for (Reservation r : page.getContent()) {
            if (r.getStatut() == StatutReservation.ANNULEE) {
                continue;
            }
            LocalDate dStart = r.getDateArrivee();
            LocalDate dEnd = r.getDateDepart();
            if (dStart == null || dEnd == null) {
                continue;
            }
            LocalDate effStart = dStart.isBefore(from) ? from : dStart;
            LocalDate effEnd = dEnd.isAfter(to) ? to : dEnd;
            if (!effStart.isBefore(effEnd)) {
                continue;
            }
            total += java.time.temporal.ChronoUnit.DAYS.between(effStart, effEnd);
        }
        return total;
    }

    private static long nz(Long value) {
        return value == null ? 0L : value;
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static void validate(LocalDate from, LocalDate to, int limit) {
        if (from == null || to == null) {
            throw new BusinessException("error.report.dateRange.required");
        }
        if (!from.isBefore(to)) {
            throw new BusinessException("error.report.dateRange.invalid");
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new BusinessException("error.report.limit.outOfRange");
        }
    }
}

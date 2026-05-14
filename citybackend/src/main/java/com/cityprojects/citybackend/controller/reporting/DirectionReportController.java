package com.cityprojects.citybackend.controller.reporting;

import com.cityprojects.citybackend.dto.reporting.DashboardDirectionDto;
import com.cityprojects.citybackend.service.reporting.DashboardDirectionReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * REST API direction reporting P2 (Tour 41).
 * <ul>
 *   <li>R-DIR-001 Dashboard direction agrege (occupation, CA, alertes, taches, KPIs).</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/reports/direction")
public class DirectionReportController {

    private final DashboardDirectionReportService dashboardService;

    public DirectionReportController(DashboardDirectionReportService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT')")
    public ResponseEntity<DashboardDirectionDto> getDashboard(
            @RequestParam(name = "date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(dashboardService.computeDashboard(date));
    }
}

package com.cityprojects.citybackend.service.reporting.export;

import com.cityprojects.citybackend.exception.BusinessException;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation JasperReports (Tour 40 MVP).
 *
 * <p>Cache des {@link JasperReport} compiles ({@code ConcurrentHashMap}) pour eviter
 * la recompilation a chaque rapport (la compilation jrxml est couteuse).</p>
 */
@Service
public class PdfExportServiceImpl implements PdfExportService {

    private static final Logger log = LoggerFactory.getLogger(PdfExportServiceImpl.class);

    private final Map<String, JasperReport> compiledCache = new ConcurrentHashMap<>();

    @Override
    public byte[] exportToPdf(String templateName, Map<String, Object> parameters, List<?> dataSource) {
        if (templateName == null || templateName.isBlank()) {
            throw new IllegalArgumentException("templateName must not be blank");
        }
        try {
            JasperReport report = compiledCache.computeIfAbsent(templateName, this::compile);
            Map<String, Object> safeParams = parameters != null ? parameters : new HashMap<>();
            List<?> safeData = dataSource != null ? dataSource : List.of();
            JasperPrint print = JasperFillManager.fillReport(
                    report, safeParams, new JRBeanCollectionDataSource(safeData));
            return JasperExportManager.exportReportToPdf(print);
        } catch (JRException e) {
            // Logger + cle i18n metier (pas de catch silencieux)
            log.error("PDF export failed for template {}", templateName, e);
            throw new BusinessException("error.report.export.pdf.failed");
        }
    }

    private JasperReport compile(String templateName) {
        String path = "reports/" + templateName + ".jrxml";
        try (InputStream is = new ClassPathResource(path).getInputStream()) {
            return JasperCompileManager.compileReport(is);
        } catch (Exception e) {
            log.error("Failed to compile Jasper template {}", path, e);
            throw new BusinessException("error.report.template.compile.failed");
        }
    }
}

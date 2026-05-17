package com.cityprojects.citybackend.service.reporting.export;

import com.cityprojects.citybackend.exception.BusinessException;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.lang.reflect.RecordComponent;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
                    report, safeParams, buildDataSource(safeData));
            return JasperExportManager.exportReportToPdf(print);
        } catch (JRException e) {
            // Logger + cle i18n metier (pas de catch silencieux)
            log.error("PDF export failed for template {}", templateName, e);
            throw new BusinessException("error.report.export.pdf.failed");
        }
    }

    /**
     * Choisit le DataSource Jasper adapté au type des éléments.
     *
     * <p><b>Workaround Jasper 6.21 + Java records</b> : {@link JRBeanCollectionDataSource}
     * utilise l'introspection JavaBean ({@code getXxx()}) qui ne reconnaît pas les
     * accesseurs de records Java 16+ ({@code xxx()} sans préfixe). Si la liste
     * contient des records, on les convertit en {@code Map<String, Object>} et on
     * utilise {@link JRMapCollectionDataSource} qui matche les {@code $F{xxx}} du
     * jrxml par clé de map. Sinon (POJO classique) on garde le bean DataSource.</p>
     */
    private static net.sf.jasperreports.engine.JRDataSource buildDataSource(List<?> data) {
        if (data.isEmpty()) {
            return new JRBeanCollectionDataSource(data);
        }
        Object sample = data.get(0);
        if (sample != null && sample.getClass().isRecord()) {
            List<Map<String, Object>> asMaps = data.stream()
                    .map(PdfExportServiceImpl::recordToMap)
                    .toList();
            // Cast intermédiaire requis par les génériques Java
            // (List<Map<String, Object>> non assignable à List<Map<String, ?>>)
            @SuppressWarnings({ "unchecked", "rawtypes" })
            List<Map<String, ?>> dataSrc = (List) asMaps;
            return new JRMapCollectionDataSource(dataSrc);
        }
        return new JRBeanCollectionDataSource(data);
    }

    /** Convertit un record en {@code Map<componentName, value>} via {@code RecordComponent}. */
    private static Map<String, Object> recordToMap(Object rec) {
        if (rec == null) {
            return Map.of();
        }
        RecordComponent[] components = rec.getClass().getRecordComponents();
        Map<String, Object> map = new LinkedHashMap<>(components.length);
        for (RecordComponent c : components) {
            try {
                map.put(c.getName(), c.getAccessor().invoke(rec));
            } catch (ReflectiveOperationException e) {
                log.warn("Cannot read record component {} on {}", c.getName(),
                        rec.getClass().getSimpleName(), e);
            }
        }
        return map;
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

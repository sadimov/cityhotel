package com.cityprojects.citybackend.service.reporting.export;

import java.util.List;
import java.util.Map;

/**
 * Wrapper JasperReports 6.21.3 pour les exports PDF (Tour 40 MVP).
 *
 * <p>L'implementation compile/cache les {@code .jrxml} a la demande et exporte
 * en PDF. Les data sources sont des {@code List<?>} typees ; les parametres
 * additionnels (titres, periode, hotel) passent par {@link Map}.</p>
 */
public interface PdfExportService {

    /**
     * Compile le template {@code classpath:reports/{templateName}.jrxml}, l'execute
     * sur la data source fournie et retourne le PDF binaire.
     *
     * @param templateName nom du template sans extension (ex. "occupation", "night-audit")
     * @param parameters   parametres scalaires injectes dans le template (titre, periode, hotel...)
     * @param dataSource   collection a iterer (1 ligne par item)
     * @return binaire PDF
     */
    byte[] exportToPdf(String templateName, Map<String, Object> parameters, List<?> dataSource);
}

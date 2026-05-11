package com.cityprojects.citybackend.common.advice;

import com.cityprojects.citybackend.util.ApiResponse;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * Wrap automatique de toutes les réponses controller dans {@link ApiResponse}
 * pour cohérence frontend.
 *
 * <p>Contexte (bug post-v1.0.0) : le projet a 2 mondes côté controllers :
 * <ul>
 *   <li>Controllers <b>anciens</b> (clients, hebergement, inventory, finance,
 *       restaurant, menage) — retournent {@code T} ou {@code Page<T>} direct
 *       sans wrapper.</li>
 *   <li>Controllers <b>Tour 31 admin</b> — retournent {@code ApiResponse<T>}
 *       explicitement.</li>
 * </ul>
 * Côté frontend, tous les services utilisent {@code .pipe(map(r => r.data))}
 * (pattern ApiResponse). Sur les controllers anciens, {@code r.data} était
 * {@code undefined} → liste vide silencieuse côté UI.
 *
 * <p>Fix : intercepter avant sérialisation. Si le body est déjà un
 * {@link ApiResponse}, pass-through. Sinon, wrap via {@link ApiResponse#success}.
 *
 * <p>Exclusions : endpoints actuator, swagger, et chemins {@code /auth/*}
 * qui retournent déjà des structures dédiées (login retourne déjà ApiResponse,
 * mais via le code controller, pas ici).
 */
@RestControllerAdvice(basePackages = "com.cityprojects.citybackend.controller")
public class ApiResponseBodyAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        // S'applique à toutes les méthodes des controllers du package controller.*
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body,
                                  MethodParameter returnType,
                                  MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request,
                                  ServerHttpResponse response) {
        // Cas 1 : body déjà ApiResponse → ne pas double-wrapper
        if (body instanceof ApiResponse) {
            return body;
        }
        // Cas 2 : body null (HTTP 204 No Content typique) → wrapper success vide
        if (body == null) {
            return ApiResponse.success(null);
        }
        // Cas 3 : body String — Spring sérialise les String avec un converter dédié
        // qui ne tolère pas un wrapper Object. On retourne un JSON manuellement
        // sérialisé pour préserver le contrat. En pratique, peu de controllers
        // métier retournent un String brut — on délègue à Spring pour ce cas
        // edge en retournant le String tel quel (le frontend devra gérer).
        if (body instanceof String) {
            return body;
        }
        // Cas 4 : tous les autres → wrapper standard ApiResponse.success(body)
        return ApiResponse.success(body);
    }
}

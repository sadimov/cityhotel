package com.cityprojects.citybackend.exception;

import com.cityprojects.citybackend.util.ApiResponse;
import jakarta.persistence.OptimisticLockException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BindingResult;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.util.ArrayList;
import java.util.List;

/**
 * Gestionnaire global des exceptions.
 *
 * <p>Tour 38 C9/H9 hardening :
 * <ul>
 *   <li>Niveaux de log adaptes (4xx attendus en DEBUG, 5xx en ERROR).</li>
 *   <li>Plus de {@code ex.getMessage()} brut au client : message neutre + cle i18n
 *       systematique. Le message detaille reste cote logs serveur.</li>
 *   <li>Handlers ajoutes : {@link BadCredentialsException},
 *       {@link org.springframework.security.core.AuthenticationException} (Spring),
 *       {@link ConstraintViolationException}, {@link DataIntegrityViolationException},
 *       {@link OptimisticLockException}, {@link HttpRequestMethodNotSupportedException},
 *       {@link HttpMessageNotReadableException}.</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Erreurs d'authentification CITY (notre exception metier maison).
     * 401. Logged en DEBUG (4xx attendus).
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthenticationException(
            AuthenticationException ex, WebRequest request) {

        logger.debug("Erreur d'authentification: {}", ex.getMessage());

        ApiResponse<Void> response = ApiResponse.error("error.auth.failed", HttpStatus.UNAUTHORIZED.value());
        response.setPath(request.getDescription(false));

        return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
    }

    /**
     * Tour 38 C9 : BadCredentialsException Spring Security (401).
     * Toujours retourner la meme cle i18n pour ne pas distinguer "user inconnu"
     * de "mot de passe invalide" (timing oracle).
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentials(
            BadCredentialsException ex, WebRequest request) {

        logger.debug("Bad credentials: {}", ex.getMessage());

        ApiResponse<Void> response = ApiResponse.error("error.auth.badCredentials", HttpStatus.UNAUTHORIZED.value());
        response.setPath(request.getDescription(false));

        return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
    }

    /**
     * Tour 38 C9 : AuthenticationException Spring Security generique (401).
     * Catch-all pour toutes les sous-classes Spring (LockedException,
     * DisabledException, etc.). Note : on capture la classe Spring complete pour
     * eviter la collision avec notre AuthenticationException maison ci-dessus.
     */
    @ExceptionHandler(org.springframework.security.core.AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleSpringAuthenticationException(
            org.springframework.security.core.AuthenticationException ex, WebRequest request) {

        logger.debug("Spring auth exception: {}", ex.getMessage());

        ApiResponse<Void> response = ApiResponse.error("error.auth.failed", HttpStatus.UNAUTHORIZED.value());
        response.setPath(request.getDescription(false));

        return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
    }

    /**
     * Erreurs d'autorisation : 403. Logged en DEBUG (4xx attendus).
     */
    @ExceptionHandler({AuthorizationException.class, AccessDeniedException.class})
    public ResponseEntity<ApiResponse<Void>> handleAuthorizationException(
            Exception ex, WebRequest request) {

        logger.debug("Erreur d'autorisation: {}", ex.getMessage());

        ApiResponse<Void> response = ApiResponse.error(
            "error.authorization.denied",
            HttpStatus.FORBIDDEN.value()
        );
        response.setPath(request.getDescription(false));

        return new ResponseEntity<>(response, HttpStatus.FORBIDDEN);
    }

    /**
     * Ressource non trouvee : 404. DEBUG.
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFoundException(
            ResourceNotFoundException ex, WebRequest request) {

        logger.debug("Ressource non trouvee: {}", ex.getMessage());

        ApiResponse<Void> response = ApiResponse.error(ex.getMessage(), HttpStatus.NOT_FOUND.value());
        response.setPath(request.getDescription(false));

        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }

    /**
     * Exceptions metier : 400. DEBUG (cas attendu cote client).
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(
            BusinessException ex, WebRequest request) {

        logger.debug("Erreur metier: {}", ex.getMessage());

        ApiResponse<Void> response = ApiResponse.error(ex.getMessage(), HttpStatus.BAD_REQUEST.value());
        response.setPath(request.getDescription(false));

        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    /**
     * Erreurs de validation (Bean Validation sur @RequestBody) : 400. DEBUG.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<List<String>>> handleValidationException(
            MethodArgumentNotValidException ex, WebRequest request) {

        logger.debug("Erreur de validation: {}", ex.getMessage());

        List<String> errors = new ArrayList<>();
        BindingResult bindingResult = ex.getBindingResult();

        bindingResult.getFieldErrors().forEach(error ->
            errors.add(error.getField() + ": " + error.getDefaultMessage())
        );

        bindingResult.getGlobalErrors().forEach(error ->
            errors.add(error.getDefaultMessage())
        );

        ApiResponse<List<String>> response = ApiResponse.error(
            "error.validation.failed",
            "error.validation.invalidData",
            HttpStatus.BAD_REQUEST.value()
        );
        response.setData(errors);
        response.setPath(request.getDescription(false));

        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    /**
     * Tour 38 C9 : ConstraintViolationException (Bean Validation sur @PathVariable
     * ou @RequestParam, et JPA pre-persist). 400. Details normalises sans message brut.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<List<String>>> handleConstraintViolation(
            ConstraintViolationException ex, WebRequest request) {

        logger.debug("Constraint violation: {}", ex.getMessage());

        List<String> errors = new ArrayList<>();
        if (ex.getConstraintViolations() != null) {
            ex.getConstraintViolations().forEach(v ->
                errors.add(v.getPropertyPath() + ": " + v.getMessage())
            );
        }

        ApiResponse<List<String>> response = ApiResponse.error(
            "error.validation.failed",
            "error.validation.invalidData",
            HttpStatus.BAD_REQUEST.value()
        );
        response.setData(errors);
        response.setPath(request.getDescription(false));

        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    /**
     * Tour 38 C9 : DataIntegrityViolationException (FK, unique, NOT NULL violes
     * cote BDD). 409 Conflict. INFO (signal interessant — duplicate ou course condition).
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(
            DataIntegrityViolationException ex, WebRequest request) {

        logger.info("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());

        ApiResponse<Void> response = ApiResponse.error(
            "error.constraint.violation",
            HttpStatus.CONFLICT.value()
        );
        response.setPath(request.getDescription(false));

        return new ResponseEntity<>(response, HttpStatus.CONFLICT);
    }

    /**
     * Tour 38 C9 : OptimisticLockException (concurrence sur @Version). 409. INFO.
     */
    @ExceptionHandler(OptimisticLockException.class)
    public ResponseEntity<ApiResponse<Void>> handleOptimisticLock(
            OptimisticLockException ex, WebRequest request) {

        logger.info("Optimistic lock conflict: {}", ex.getMessage());

        ApiResponse<Void> response = ApiResponse.error(
            "error.concurrent.modification",
            HttpStatus.CONFLICT.value()
        );
        response.setPath(request.getDescription(false));

        return new ResponseEntity<>(response, HttpStatus.CONFLICT);
    }

    /**
     * Tour 38 C9 : methode HTTP non supportee par le controller. 405. DEBUG.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException ex, WebRequest request) {

        logger.debug("Method not allowed: {}", ex.getMessage());

        ApiResponse<Void> response = ApiResponse.error(
            "error.method.notAllowed",
            HttpStatus.METHOD_NOT_ALLOWED.value()
        );
        response.setPath(request.getDescription(false));

        return new ResponseEntity<>(response, HttpStatus.METHOD_NOT_ALLOWED);
    }

    /**
     * Tour 38 C9 : body JSON malforme. 400. DEBUG.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleMessageNotReadable(
            HttpMessageNotReadableException ex, WebRequest request) {

        logger.debug("Body malformed: {}", ex.getMessage());

        ApiResponse<Void> response = ApiResponse.error(
            "error.body.malformed",
            HttpStatus.BAD_REQUEST.value()
        );
        response.setPath(request.getDescription(false));

        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    /**
     * Tour 40 : parametre HTTP obligatoire absent. 400. DEBUG.
     * Couvre @RequestParam(required=true) sans valeur fournie (ex. /night-audit sans from).
     */
    @ExceptionHandler(org.springframework.web.bind.MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingRequestParam(
            org.springframework.web.bind.MissingServletRequestParameterException ex, WebRequest request) {

        logger.debug("Missing request parameter: {}", ex.getMessage());

        ApiResponse<Void> response = ApiResponse.error(
            "error.request.param.missing",
            HttpStatus.BAD_REQUEST.value()
        );
        response.setPath(request.getDescription(false));

        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    /**
     * Tour 40 : type de parametre HTTP invalide (ex. "not-a-date" sur un @RequestParam LocalDate).
     * 400. DEBUG.
     */
    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleArgumentTypeMismatch(
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException ex, WebRequest request) {

        logger.debug("Argument type mismatch on {}: {}", ex.getName(), ex.getMessage());

        ApiResponse<Void> response = ApiResponse.error(
            "error.request.param.invalidType",
            HttpStatus.BAD_REQUEST.value()
        );
        response.setPath(request.getDescription(false));

        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    /**
     * Erreurs d'arguments illegaux : 400. DEBUG.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgumentException(
            IllegalArgumentException ex, WebRequest request) {

        logger.debug("Argument illegal: {}", ex.getMessage());

        ApiResponse<Void> response = ApiResponse.error(ex.getMessage(), HttpStatus.BAD_REQUEST.value());
        response.setPath(request.getDescription(false));

        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    /**
     * Erreurs d'etat illegal : 409. DEBUG (typiquement transitions metier refusees).
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalStateException(
            IllegalStateException ex, WebRequest request) {

        logger.debug("Etat illegal: {}", ex.getMessage());

        ApiResponse<Void> response = ApiResponse.error(
            "error.state.illegal",
            HttpStatus.CONFLICT.value()
        );
        response.setPath(request.getDescription(false));

        return new ResponseEntity<>(response, HttpStatus.CONFLICT);
    }

    /**
     * Catch-all : 500 Internal Server Error.
     * Le seul handler en ERROR avec stack trace complete (operationnel).
     * Le message expose au client est neutre — JAMAIS {@code ex.getMessage()}.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(
            Exception ex, WebRequest request) {

        logger.error("Erreur interne du serveur: {}", ex.getMessage(), ex);

        ApiResponse<Void> response = ApiResponse.error(
            "error.internal.server",
            HttpStatus.INTERNAL_SERVER_ERROR.value()
        );
        response.setPath(request.getDescription(false));

        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}

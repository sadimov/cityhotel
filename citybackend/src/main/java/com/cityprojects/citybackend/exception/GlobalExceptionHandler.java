package com.cityprojects.citybackend.exception;

import com.cityprojects.citybackend.util.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.util.ArrayList;
import java.util.List;

/**
 * Gestionnaire global des exceptions
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Gestion des erreurs d'authentification
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthenticationException(
            AuthenticationException ex, WebRequest request) {
        
        logger.error("Erreur d'authentification: {}", ex.getMessage(), ex);
        
        ApiResponse<Void> response = ApiResponse.error(ex.getMessage(), HttpStatus.UNAUTHORIZED.value());
        response.setPath(request.getDescription(false));
        
        return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
    }

    /**
     * Gestion des erreurs d'autorisation
     */
    @ExceptionHandler({AuthorizationException.class, AccessDeniedException.class})
    public ResponseEntity<ApiResponse<Void>> handleAuthorizationException(
            Exception ex, WebRequest request) {
        
        logger.error("Erreur d'autorisation: {}", ex.getMessage(), ex);
        
        ApiResponse<Void> response = ApiResponse.error(
            "Accès refusé. Vous n'avez pas les permissions nécessaires.", 
            HttpStatus.FORBIDDEN.value()
        );
        response.setPath(request.getDescription(false));
        
        return new ResponseEntity<>(response, HttpStatus.FORBIDDEN);
    }

    /**
     * Gestion des ressources non trouvées
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFoundException(
            ResourceNotFoundException ex, WebRequest request) {
        
        logger.error("Ressource non trouvée: {}", ex.getMessage(), ex);
        
        ApiResponse<Void> response = ApiResponse.error(ex.getMessage(), HttpStatus.NOT_FOUND.value());
        response.setPath(request.getDescription(false));
        
        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }

    /**
     * Gestion des exceptions métier
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(
            BusinessException ex, WebRequest request) {
        
        logger.error("Erreur métier: {}", ex.getMessage(), ex);
        
        ApiResponse<Void> response = ApiResponse.error(ex.getMessage(), HttpStatus.BAD_REQUEST.value());
        response.setPath(request.getDescription(false));
        
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    /**
     * Gestion des erreurs de validation
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<List<String>>> handleValidationException(
            MethodArgumentNotValidException ex, WebRequest request) {
        
        logger.error("Erreur de validation: {}", ex.getMessage(), ex);
        
        List<String> errors = new ArrayList<>();
        BindingResult bindingResult = ex.getBindingResult();
        
        bindingResult.getFieldErrors().forEach(error -> 
            errors.add(error.getField() + ": " + error.getDefaultMessage())
        );
        
        bindingResult.getGlobalErrors().forEach(error -> 
            errors.add(error.getDefaultMessage())
        );
        
        ApiResponse<List<String>> response = ApiResponse.error(
            "Erreurs de validation", 
            "Données invalides", 
            HttpStatus.BAD_REQUEST.value()
        );
        response.setData(errors);
        response.setPath(request.getDescription(false));
        
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    /**
     * Gestion des erreurs génériques
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(
            Exception ex, WebRequest request) {
        
        logger.error("Erreur interne du serveur: {}", ex.getMessage(), ex);
        
        ApiResponse<Void> response = ApiResponse.error(
            "Une erreur interne s'est produite. Veuillez réessayer plus tard.", 
            HttpStatus.INTERNAL_SERVER_ERROR.value()
        );
        response.setPath(request.getDescription(false));
        
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * Gestion des erreurs d'arguments illégaux
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgumentException(
            IllegalArgumentException ex, WebRequest request) {
        
        logger.error("Argument illégal: {}", ex.getMessage(), ex);
        
        ApiResponse<Void> response = ApiResponse.error(ex.getMessage(), HttpStatus.BAD_REQUEST.value());
        response.setPath(request.getDescription(false));
        
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    /**
     * Gestion des erreurs d'état illégal
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalStateException(
            IllegalStateException ex, WebRequest request) {
        
        logger.error("État illégal: {}", ex.getMessage(), ex);
        
        ApiResponse<Void> response = ApiResponse.error(
            "Opération non autorisée dans l'état actuel", 
            HttpStatus.CONFLICT.value()
        );
        response.setPath(request.getDescription(false));
        
        return new ResponseEntity<>(response, HttpStatus.CONFLICT);
    }
}
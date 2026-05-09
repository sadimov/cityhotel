package com.cityprojects.citybackend.exception;

/**
 * Exception personnalisée pour les erreurs d'autorisation
 */
public class AuthorizationException extends RuntimeException {
    
    public AuthorizationException(String message) {
        super(message);
    }

    public AuthorizationException(String message, Throwable cause) {
        super(message, cause);
    }
}
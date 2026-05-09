package com.cityprojects.citybackend.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.security.SecureRandom;
import java.util.regex.Pattern;

/**
 * Utilitaire pour la gestion des mots de passe avec BCrypt
 * Version simplifiée sans salt séparé (BCrypt inclut le salt)
 */
public class PasswordUtil {

    private static final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(12);
    private static final SecureRandom random = new SecureRandom();
    
    // Patterns de validation
    private static final Pattern UPPERCASE_PATTERN = Pattern.compile(".*[A-Z].*");
    private static final Pattern LOWERCASE_PATTERN = Pattern.compile(".*[a-z].*");
    private static final Pattern DIGIT_PATTERN = Pattern.compile(".*[0-9].*");
    private static final Pattern SPECIAL_CHAR_PATTERN = Pattern.compile(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?].*");

    /**
     * Hash un mot de passe avec BCrypt (salt inclus automatiquement)
     */
    public static String hashPassword(String password) {
        if (password == null || password.trim().isEmpty()) {
            throw new IllegalArgumentException("Le mot de passe ne peut pas être vide");
        }
        return passwordEncoder.encode(password);
    }

    /**
     * Vérifie un mot de passe contre son hash BCrypt
     */
    public static boolean verifyPassword(String password, String hashedPassword) {
        if (password == null || hashedPassword == null) {
            return false;
        }
        return passwordEncoder.matches(password, hashedPassword);
    }

    /**
     * Méthode de compatibilité avec l'ancienne API (salt ignoré)
     * @deprecated Utiliser verifyPassword(password, hashedPassword) à la place
     */
    @Deprecated
    public static boolean verifyPassword(String password, String hashedPassword, String salt) {
        // Le salt est ignoré car BCrypt l'inclut dans le hash
        return verifyPassword(password, hashedPassword);
    }

    /**
     * Valide la force d'un mot de passe
     */
    public static PasswordValidationResult validatePassword(String password) {
        PasswordValidationResult result = new PasswordValidationResult();
        
        if (password == null || password.length() < 8) {
            result.addError("Le mot de passe doit contenir au moins 8 caractères");
        }
        
        if (password != null && password.length() > 128) {
            result.addError("Le mot de passe ne peut pas dépasser 128 caractères");
        }
        
        if (password != null) {
            if (!UPPERCASE_PATTERN.matcher(password).matches()) {
                result.addError("Le mot de passe doit contenir au moins une lettre majuscule");
            }
            
            if (!LOWERCASE_PATTERN.matcher(password).matches()) {
                result.addError("Le mot de passe doit contenir au moins une lettre minuscule");
            }
            
            if (!DIGIT_PATTERN.matcher(password).matches()) {
                result.addError("Le mot de passe doit contenir au moins un chiffre");
            }
            
            // Vérification optionnelle des caractères spéciaux
            if (!SPECIAL_CHAR_PATTERN.matcher(password).matches()) {
                result.addWarning("Il est recommandé d'inclure des caractères spéciaux");
            }
            
            // Vérifier que le mot de passe n'est pas trop simple
            if (isCommonPassword(password)) {
                result.addError("Ce mot de passe est trop commun, veuillez en choisir un autre");
            }
        }
        
        return result;
    }

    /**
     * Vérifie si le mot de passe est dans la liste des mots de passe communs
     */
    private static boolean isCommonPassword(String password) {
        String lowerPassword = password.toLowerCase();
        String[] commonPasswords = {
            "password", "123456", "123456789", "qwerty", "abc123", 
            "password123", "admin", "letmein", "welcome", "monkey",
            "dragon", "1234567890", "football", "iloveyou", "admin123",
            "azerty", "motdepasse", "password1", "123123", "000000"
        };
        
        for (String common : commonPasswords) {
            if (lowerPassword.equals(common) || 
                lowerPassword.contains(common) || 
                common.contains(lowerPassword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Génère un mot de passe aléatoire sécurisé
     */
    public static String generateSecurePassword(int length) {
        if (length < 8) {
            length = 8;
        }
        
        String upperCase = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String lowerCase = "abcdefghijklmnopqrstuvwxyz";
        String digits = "0123456789";
        String specialChars = "!@#$%^&*()_+-=[]{}|;:,.<>?";
        
        String allChars = upperCase + lowerCase + digits + specialChars;
        
        StringBuilder password = new StringBuilder();
        
        // Garantir au moins un caractère de chaque type
        password.append(upperCase.charAt(random.nextInt(upperCase.length())));
        password.append(lowerCase.charAt(random.nextInt(lowerCase.length())));
        password.append(digits.charAt(random.nextInt(digits.length())));
        password.append(specialChars.charAt(random.nextInt(specialChars.length())));
        
        // Remplir le reste
        for (int i = 4; i < length; i++) {
            password.append(allChars.charAt(random.nextInt(allChars.length())));
        }
        
        // Mélanger les caractères
        char[] passwordArray = password.toString().toCharArray();
        for (int i = passwordArray.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char temp = passwordArray[i];
            passwordArray[i] = passwordArray[j];
            passwordArray[j] = temp;
        }
        
        return new String(passwordArray);
    }

    /**
     * Vérifie si un hash de mot de passe est valide (format BCrypt)
     */
    public static boolean isValidHash(String hash) {
        if (hash == null || hash.isEmpty()) {
            return false;
        }
        
        // Format BCrypt : $2a$rounds$salt+hash (60 caractères total)
        return hash.startsWith("$2a$") || hash.startsWith("$2b$") || hash.startsWith("$2y$") && 
               hash.length() == 60;
    }

    /**
     * Extrait le coût (rounds) d'un hash BCrypt
     */
    public static int extractCost(String hash) {
        if (!isValidHash(hash)) {
            return -1;
        }
        
        try {
            String[] parts = hash.split("\\$");
            if (parts.length >= 3) {
                return Integer.parseInt(parts[2]);
            }
        } catch (NumberFormatException e) {
            // Ignorer l'erreur
        }
        
        return -1;
    }

    /**
     * Classe pour le résultat de validation de mot de passe
     */
    public static class PasswordValidationResult {
        private java.util.List<String> errors = new java.util.ArrayList<>();
        private java.util.List<String> warnings = new java.util.ArrayList<>();
        
        public void addError(String error) {
            errors.add(error);
        }
        
        public void addWarning(String warning) {
            warnings.add(warning);
        }
        
        public boolean isValid() {
            return errors.isEmpty();
        }
        
        public java.util.List<String> getErrors() {
            return errors;
        }
        
        public java.util.List<String> getWarnings() {
            return warnings;
        }
        
        public String getFirstError() {
            return errors.isEmpty() ? null : errors.get(0);
        }
        
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            if (!errors.isEmpty()) {
                sb.append("Erreurs: ").append(errors);
            }
            if (!warnings.isEmpty()) {
                if (sb.length() > 0) sb.append(", ");
                sb.append("Avertissements: ").append(warnings);
            }
            return sb.toString();
        }
    }
}
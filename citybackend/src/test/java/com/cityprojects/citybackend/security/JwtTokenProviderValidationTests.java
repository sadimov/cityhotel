package com.cityprojects.citybackend.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests Surefire de la validation post-construction de {@link JwtTokenProvider}
 * (Tour 38 H4).
 *
 * <p>Verifie le fail-fast au boot pour : secret null, secret hardcoded historique,
 * secret trop court. Et accepte un secret legitime.</p>
 */
class JwtTokenProviderValidationTests {

    private static final String LEGIT_SECRET =
            "an-extremely-long-secure-random-jwt-secret-for-junit-only-please-rotate-immediately";

    private JwtTokenProvider build(String secret) {
        JwtTokenProvider provider = new JwtTokenProvider();
        ReflectionTestUtils.setField(provider, "jwtSecret", secret);
        ReflectionTestUtils.setField(provider, "jwtExpirationInMs", 3_600_000);
        return provider;
    }

    private void invokeValidate(JwtTokenProvider provider) {
        // La methode validate() est package-private — appelable directement.
        provider.validate();
    }

    @Test
    @DisplayName("Secret null : IllegalStateException au boot")
    void nullSecret_failsFast() {
        JwtTokenProvider provider = build(null);
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> invokeValidate(provider));
        assertTrue(ex.getMessage().contains("JWT secret is null or blank"),
                "Message must mention null/blank secret");
    }

    @Test
    @DisplayName("Secret blank : IllegalStateException au boot")
    void blankSecret_failsFast() {
        JwtTokenProvider provider = build("   ");
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> invokeValidate(provider));
        assertTrue(ex.getMessage().contains("JWT secret is null or blank"));
    }

    @Test
    @DisplayName("Secret hardcoded legacy 'mySecretKey...' : refuse au boot")
    void legacyHardcodedSecret_failsFast() {
        JwtTokenProvider provider = build("mySecretKey12345mySecretKey12345mySecretKey12345aaaaaaaaaaaaaaaaaaa");
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> invokeValidate(provider));
        assertTrue(ex.getMessage().contains("legacy hardcoded default"),
                "Message must mention legacy default");
    }

    @Test
    @DisplayName("Secret trop court (< 64 chars) : refuse au boot")
    void shortSecret_failsFast() {
        JwtTokenProvider provider = build("short-secret-not-long-enough-for-prod"); // 37 chars
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> invokeValidate(provider));
        assertTrue(ex.getMessage().contains("too short"),
                "Message must mention 'too short'");
    }

    @Test
    @DisplayName("Secret legitime (>= 64 chars, pas de prefixe legacy) : passe sans erreur")
    void legitSecret_passes() {
        JwtTokenProvider provider = build(LEGIT_SECRET);
        assertDoesNotThrow(() -> invokeValidate(provider));
    }
}

package com.cityprojects.citybackend.security;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.service.hebergement.NightAuditLockRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires du {@link NightAuditLockFilter}.
 *
 * <p>Couvre la matrice (méthode, path, tenant verrouillé) sans démarrer le
 * contexte Spring (Mockito pour {@link NightAuditLockRegistry}, MockHttpServlet*
 * pour la requête/réponse).</p>
 */
@ExtendWith(MockitoExtension.class)
class NightAuditLockFilterTests {

    @Mock
    private NightAuditLockRegistry lockRegistry;

    @Mock
    private FilterChain filterChain;

    private NightAuditLockFilter filter;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        // En prod Spring Boot autoconfigure JavaTimeModule sur l'ObjectMapper
        // exposé en bean ; ici on doit l'enregistrer manuellement pour
        // sérialiser ApiResponse.timestamp (LocalDateTime).
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        filter = new NightAuditLockFilter(lockRegistry, objectMapper);
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("POST + tenant verrouillé → 423 Locked + clé i18n error.nightAudit.inProgress")
    void post_lockedTenant_returns423() throws Exception {
        TenantContext.set(42L);
        when(lockRegistry.isLocked(42L)).thenReturn(true);

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/clients");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilter(req, resp, filterChain);

        assertEquals(423, resp.getStatus(), "Status 423 Locked attendu");
        assertTrue(resp.getContentType() != null && resp.getContentType().startsWith("application/json"),
                "Content-Type attendu: application/json* — reçu: " + resp.getContentType());
        String body = resp.getContentAsString();
        assertTrue(body.contains("error.nightAudit.inProgress"),
                "Le payload doit porter la clé i18n attendue. Reçu: " + body);
        verify(filterChain, never()).doFilter(req, resp);
    }

    @Test
    @DisplayName("GET + tenant verrouillé → passe (lectures toujours autorisées)")
    void get_lockedTenant_bypassed() throws Exception {
        TenantContext.set(42L);
        // isLocked() ne devrait même pas être appelé pour un GET (méthode bypass).

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/clients");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilter(req, resp, filterChain);

        verify(filterChain, times(1)).doFilter(req, resp);
        verify(lockRegistry, never()).isLocked(42L);
        assertEquals(200, resp.getStatus(), "GET passe — pas de modification de status");
    }

    @Test
    @DisplayName("POST /auth/login → exempté même si tenant verrouillé")
    void postAuthLogin_exempted() throws Exception {
        TenantContext.set(42L);
        // isLocked ne doit même pas être appelé (path exclu)

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/auth/login");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilter(req, resp, filterChain);

        verify(filterChain, times(1)).doFilter(req, resp);
        verify(lockRegistry, never()).isLocked(42L);
    }

    @Test
    @DisplayName("POST /api/hebergement/night-audit/run → exempté (sinon impossible de déverrouiller)")
    void postNightAuditRun_exempted() throws Exception {
        TenantContext.set(42L);

        MockHttpServletRequest req = new MockHttpServletRequest("POST",
                "/api/hebergement/night-audit/run");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilter(req, resp, filterChain);

        verify(filterChain, times(1)).doFilter(req, resp);
        verify(lockRegistry, never()).isLocked(42L);
    }

    @Test
    @DisplayName("POST + tenant non verrouillé → passe normalement")
    void post_unlockedTenant_passes() throws Exception {
        TenantContext.set(42L);
        when(lockRegistry.isLocked(42L)).thenReturn(false);

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/clients");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilter(req, resp, filterChain);

        verify(filterChain, times(1)).doFilter(req, resp);
        assertEquals(200, resp.getStatus(), "Pas de status d'erreur");
    }

    @Test
    @DisplayName("POST sans tenant (SUPERADMIN root, anonyme) → passe sans check registry")
    void post_noTenant_passes() throws Exception {
        // Pas de TenantContext.set() : tenant absent

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/admin/hotels");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilter(req, resp, filterChain);

        verify(filterChain, times(1)).doFilter(req, resp);
        verify(lockRegistry, never()).isLocked(any());
    }

    @Test
    @DisplayName("PATCH + tenant verrouillé → 423 (PATCH = write modifiant)")
    void patch_lockedTenant_blocked() throws Exception {
        TenantContext.set(42L);
        when(lockRegistry.isLocked(42L)).thenReturn(true);

        MockHttpServletRequest req = new MockHttpServletRequest("PATCH",
                "/api/hebergement/reservations/1");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilter(req, resp, filterChain);

        assertEquals(423, resp.getStatus());
        verify(filterChain, never()).doFilter(req, resp);
    }

    @Test
    @DisplayName("DELETE + tenant verrouillé → 423")
    void delete_lockedTenant_blocked() throws Exception {
        TenantContext.set(42L);
        when(lockRegistry.isLocked(42L)).thenReturn(true);

        MockHttpServletRequest req = new MockHttpServletRequest("DELETE", "/api/clients/1");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilter(req, resp, filterChain);

        assertEquals(423, resp.getStatus());
        verify(filterChain, never()).doFilter(req, resp);
    }

    @Test
    @DisplayName("OPTIONS (preflight CORS) → passe même si tenant verrouillé")
    void options_lockedTenant_bypassed() throws Exception {
        TenantContext.set(42L);

        MockHttpServletRequest req = new MockHttpServletRequest("OPTIONS", "/api/clients");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilter(req, resp, filterChain);

        verify(filterChain, times(1)).doFilter(req, resp);
        verify(lockRegistry, never()).isLocked(42L);
    }

    // Helper Mockito ArgumentMatchers.any() local pour éviter l'import explicite
    private static Long any() {
        return org.mockito.ArgumentMatchers.any();
    }
}

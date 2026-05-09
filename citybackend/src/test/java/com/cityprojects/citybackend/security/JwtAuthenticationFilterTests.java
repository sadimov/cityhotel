package com.cityprojects.citybackend.security;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.IOException;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires de {@link JwtAuthenticationFilter} (Tour 7B C1).
 * <p>
 * Approche : Mockito pur + {@code MockHttpServletRequest/Response} de Spring,
 * pas de boot Spring. Mock {@link JwtTokenProvider} et
 * {@link CustomUserDetailsService}. Verifie via une chaine de filtre custom
 * que le {@link TenantContext} et le {@link SecurityContextHolder} sont
 * dans l'etat attendu AU MOMENT ou la chaine continue (avant le clear()
 * du finally).
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTests {

    private static final String JWT_VALID = "valid.jwt.token";
    private static final String ROLE_GERANT = "GERANT";
    private static final String ROLE_SUPERADMIN = "SUPERADMIN";
    private static final Long USER_ID = 42L;
    private static final Long HOTEL_ID = 1L;

    @Mock
    private JwtTokenProvider tokenProvider;

    @Mock
    private CustomUserDetailsService userDetailsService;

    @InjectMocks
    private JwtAuthenticationFilter filter;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @AfterEach
    void cleanUp() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private UserDetails fakeUser(String role) {
        GrantedAuthority auth = new SimpleGrantedAuthority("ROLE_" + role);
        return new User("u-" + USER_ID, "x", Collections.singletonList(auth));
    }

    /**
     * Chaine custom qui capture l'etat du TenantContext / SecurityContext
     * AU MOMENT du doFilter, avant le clear() du finally du filtre.
     */
    private static final class CapturingChain implements FilterChain {
        boolean continued = false;
        Long tenantSeen = null;
        boolean authenticated = false;

        @Override
        public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res)
                throws IOException, ServletException {
            this.continued = true;
            this.tenantSeen = TenantContext.getOrNull();
            this.authenticated = SecurityContextHolder.getContext().getAuthentication() != null
                    && SecurityContextHolder.getContext().getAuthentication().isAuthenticated();
        }
    }

    @Test
    @DisplayName("T1 - JWT valide avec hotelId + role GERANT : auth posee, TenantContext=hotelId, chaine continue")
    void t1_validJwtWithHotelId_authenticatesAndSetsTenant() throws Exception {
        request.addHeader("Authorization", "Bearer " + JWT_VALID);
        when(tokenProvider.validateToken(JWT_VALID)).thenReturn(true);
        when(tokenProvider.getUserIdFromJWT(JWT_VALID)).thenReturn(USER_ID);
        when(tokenProvider.getHotelIdFromJWT(JWT_VALID)).thenReturn(HOTEL_ID);
        when(tokenProvider.getRoleCodeFromJWT(JWT_VALID)).thenReturn(ROLE_GERANT);
        when(userDetailsService.loadUserById(USER_ID)).thenReturn(fakeUser(ROLE_GERANT));

        CapturingChain chain = new CapturingChain();
        filter.doFilter(request, response, chain);

        assertTrue(chain.continued, "La chaine doit continuer pour un JWT valide tenant-scoped");
        assertEquals(HOTEL_ID, chain.tenantSeen,
                "TenantContext doit etre positionne au hotelId du JWT au moment du doFilter aval");
        assertTrue(chain.authenticated, "L'authentification Spring Security doit etre posee");
        // Apres le finally du filtre, TenantContext doit etre nettoye.
        assertNull(TenantContext.getOrNull(), "TenantContext doit etre clear apres le filtre");
    }

    @Test
    @DisplayName("T2 - JWT valide SANS hotelId + role SUPERADMIN : auth posee, PAS de TenantContext, chaine continue")
    void t2_validJwtSuperAdminWithoutHotelId_authenticatesInRootMode() throws Exception {
        request.addHeader("Authorization", "Bearer " + JWT_VALID);
        when(tokenProvider.validateToken(JWT_VALID)).thenReturn(true);
        when(tokenProvider.getUserIdFromJWT(JWT_VALID)).thenReturn(USER_ID);
        when(tokenProvider.getHotelIdFromJWT(JWT_VALID)).thenReturn(null);
        when(tokenProvider.getRoleCodeFromJWT(JWT_VALID)).thenReturn(ROLE_SUPERADMIN);
        when(userDetailsService.loadUserById(USER_ID)).thenReturn(fakeUser(ROLE_SUPERADMIN));

        CapturingChain chain = new CapturingChain();
        filter.doFilter(request, response, chain);

        assertTrue(chain.continued, "La chaine doit continuer pour un super-admin sans tenant");
        assertNull(chain.tenantSeen,
                "TenantContext doit rester vide en mode ROOT super-admin (pas de set)");
        assertTrue(chain.authenticated, "L'authentification doit etre posee meme sans tenant");
    }

    @Test
    @DisplayName("T3 - JWT valide SANS hotelId + role GERANT : 401, chaine NON continue, AUCUNE auth")
    void t3_validJwtNonGlobalRoleWithoutHotelId_rejected401() throws Exception {
        request.addHeader("Authorization", "Bearer " + JWT_VALID);
        when(tokenProvider.validateToken(JWT_VALID)).thenReturn(true);
        when(tokenProvider.getUserIdFromJWT(JWT_VALID)).thenReturn(USER_ID);
        when(tokenProvider.getHotelIdFromJWT(JWT_VALID)).thenReturn(null);
        when(tokenProvider.getRoleCodeFromJWT(JWT_VALID)).thenReturn(ROLE_GERANT);

        CapturingChain chain = new CapturingChain();
        filter.doFilter(request, response, chain);

        assertFalse(chain.continued, "La chaine NE doit PAS continuer pour un JWT non-global sans hotelId");
        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus(),
                "Le statut HTTP doit etre 401 Unauthorized");
        assertNull(SecurityContextHolder.getContext().getAuthentication(),
                "Aucune authentification ne doit etre posee");
        // loadUserById ne doit pas avoir ete appele : on rejette AVANT.
        verify(userDetailsService, never()).loadUserById(eq(USER_ID));
    }

    @Test
    @DisplayName("T4 - Pas de header Authorization : chaine continue, TenantContext non set, pas d'auth")
    void t4_noAuthHeader_chainContinues() throws Exception {
        // Pas de header — cas /auth/login, /actuator, etc.
        CapturingChain chain = new CapturingChain();
        filter.doFilter(request, response, chain);

        assertTrue(chain.continued, "La chaine doit continuer meme sans header Authorization");
        assertNull(chain.tenantSeen, "TenantContext doit rester vide");
        assertFalse(chain.authenticated, "Pas d'auth posee");
        assertEquals(HttpServletResponse.SC_OK, response.getStatus(),
                "Status par defaut 200 — Spring Security decidera en aval si l'endpoint exige une auth");
        // tokenProvider.validateToken ne doit pas avoir ete appele.
        verify(tokenProvider, never()).validateToken(anyString());
    }

    @Test
    @DisplayName("Bonus - JWT present mais invalide : 401, chaine NON continue")
    void invalidJwt_rejected401() throws Exception {
        request.addHeader("Authorization", "Bearer bad.jwt");
        when(tokenProvider.validateToken("bad.jwt")).thenReturn(false);

        CapturingChain chain = new CapturingChain();
        filter.doFilter(request, response, chain);

        assertFalse(chain.continued);
        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}

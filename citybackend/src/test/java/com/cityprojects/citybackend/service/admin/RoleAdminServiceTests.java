package com.cityprojects.citybackend.service.admin;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.dto.admin.RoleAdminDto;
import com.cityprojects.citybackend.entity.core.Role;
import com.cityprojects.citybackend.exception.ResourceNotFoundException;
import com.cityprojects.citybackend.repository.core.RoleRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests Surefire (H2 + Spring) du {@link RoleAdminService}.
 *
 * <p>Read-only : couverture findAll() + findById().</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class RoleAdminServiceTests {

    @Autowired
    private RoleAdminService roleAdminService;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        jdbcTemplate.update("DELETE FROM core.dbusers");
        jdbcTemplate.update("DELETE FROM core.roles");

        Role gerant = new Role("GERANT", "Gerant");
        gerant.setDescription("Gerant d'hotel");
        gerant.setActif(Boolean.TRUE);
        roleRepository.saveAndFlush(gerant);

        Role reception = new Role("RECEPTION", "Reception");
        reception.setActif(Boolean.TRUE);
        roleRepository.saveAndFlush(reception);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        jdbcTemplate.update("DELETE FROM core.dbusers");
        jdbcTemplate.update("DELETE FROM core.roles");
    }

    @Test
    @DisplayName("T1 - findAll() retourne tous les roles seedes, tries par roleNom")
    void shouldFindAllRoles() {
        List<RoleAdminDto> roles = roleAdminService.findAll();
        assertNotNull(roles);
        assertEquals(2, roles.size());
        // Tries par roleNom -> "Gerant" avant "Reception"
        assertEquals("GERANT", roles.get(0).roleCode());
        assertEquals("RECEPTION", roles.get(1).roleCode());
        assertTrue(Boolean.TRUE.equals(roles.get(0).actif()));
    }

    @Test
    @DisplayName("T2 - findById() sur id inexistant -> ResourceNotFoundException")
    void shouldThrowOnFindByIdMissing() {
        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> roleAdminService.findById(999_999));
        assertEquals("error.role.notFound", ex.getMessage());
    }
}

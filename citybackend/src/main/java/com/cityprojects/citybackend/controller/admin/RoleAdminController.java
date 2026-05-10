package com.cityprojects.citybackend.controller.admin;

import com.cityprojects.citybackend.dto.admin.RoleAdminDto;
import com.cityprojects.citybackend.service.admin.RoleAdminService;
import com.cityprojects.citybackend.util.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controller REST read-only des roles disponibles dans le systeme.
 *
 * <p>Pas de POST/PUT/DELETE : les roles sont figes par le changeset
 * Liquibase {@code 011-insert-initial-roles.sql}. Toute evolution passe
 * par un nouveau changeset.</p>
 */
@RestController
@RequestMapping("/api/admin/roles")
@PreAuthorize("hasRole('SUPERADMIN')")
public class RoleAdminController {

    private final RoleAdminService roleAdminService;

    public RoleAdminController(RoleAdminService roleAdminService) {
        this.roleAdminService = roleAdminService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<RoleAdminDto>>> findAll() {
        return ResponseEntity.ok(ApiResponse.success(roleAdminService.findAll()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RoleAdminDto>> findById(@PathVariable("id") Integer id) {
        return ResponseEntity.ok(ApiResponse.success(roleAdminService.findById(id)));
    }
}

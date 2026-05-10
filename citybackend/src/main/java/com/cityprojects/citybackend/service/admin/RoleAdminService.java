package com.cityprojects.citybackend.service.admin;

import com.cityprojects.citybackend.dto.admin.RoleAdminDto;

import java.util.List;

/**
 * Service de consultation des roles disponibles dans le systeme (lecture seule).
 *
 * <h2>Pas de mutation via API</h2>
 * <p>Les roles sont definis statiquement par le changeset Liquibase
 * {@code 011-insert-initial-roles.sql}. Toute evolution de la matrice de
 * roles passe par un nouveau changeset (ne pas creer/modifier/supprimer
 * un role via REST a chaud — risque de desynchronisation avec
 * {@code @PreAuthorize("hasRole(...)")} dur dans le code).</p>
 *
 * <h2>Service technique</h2>
 * <p>Pas de {@code @RequireTenant}. Securite via
 * {@code @PreAuthorize("hasRole('SUPERADMIN')")} cote controller.</p>
 */
public interface RoleAdminService {

    /**
     * Liste de tous les roles (actifs et inactifs).
     */
    List<RoleAdminDto> findAll();

    /**
     * Recupere un role par id.
     *
     * @throws com.cityprojects.citybackend.exception.ResourceNotFoundException
     *         si le role n'existe pas.
     */
    RoleAdminDto findById(Integer roleId);
}

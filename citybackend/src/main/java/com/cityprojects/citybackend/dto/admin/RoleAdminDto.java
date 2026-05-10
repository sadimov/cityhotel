package com.cityprojects.citybackend.dto.admin;

/**
 * DTO de sortie pour {@link com.cityprojects.citybackend.entity.core.Role}
 * cote administration SUPERADMIN.
 * <p>
 * Les roles sont des references statiques figees par Liquibase (changeset
 * {@code 011-insert-initial-roles.sql}) : pas de DTO de creation/modification
 * cote API REST. Toute evolution de la matrice de roles passe par un
 * nouveau changeset Liquibase.
 */
public record RoleAdminDto(
        Integer roleId,
        String roleCode,
        String roleNom,
        String description,
        String permissions,
        Boolean actif) {
}

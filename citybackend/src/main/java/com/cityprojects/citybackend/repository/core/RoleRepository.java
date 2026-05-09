package com.cityprojects.citybackend.repository.core;

import com.cityprojects.citybackend.entity.core.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository pour l'entité Role
 */
@Repository
public interface RoleRepository extends JpaRepository<Role, Integer> {
    
    /**
     * Trouve un rôle par son code
     */
    Optional<Role> findByRoleCode(String roleCode);
    
    /**
     * Trouve un rôle par son code et actif
     */
    Optional<Role> findByRoleCodeAndActifTrue(String roleCode);
    
    /**
     * Trouve tous les rôles actifs
     */
    List<Role> findByActifTrueOrderByRoleNom();
    
    /**
     * Vérifie si un code rôle existe déjà
     */
    boolean existsByRoleCode(String roleCode);
    
    /**
     * Trouve les rôles disponibles pour les utilisateurs d'hôtel
     */
    @Query("SELECT r FROM Role r WHERE r.roleCode IN ('ADMIN', 'GERANT', 'RECEPTION', 'RESTAURANT', 'RESREC') AND r.actif = true ORDER BY r.roleNom")
    List<Role> findHotelUserRoles();
    
    /**
     * Trouve les rôles d'administration système
     */
    @Query("SELECT r FROM Role r WHERE r.roleCode IN ('SUPERADMIN') AND r.actif = true ORDER BY r.roleNom")
    List<Role> findSystemAdminRoles();
}
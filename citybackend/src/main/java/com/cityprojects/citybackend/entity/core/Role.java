package com.cityprojects.citybackend.entity.core;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;

import java.util.HashSet;
import java.util.Set;

/**
 * Entité Role - Gestion des rôles système
 */
@Entity
@Table(name = "roles", schema = "core")
public class Role {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "role_id")
    private Integer roleId;
    
    @Column(name = "role_code", unique = true, nullable = false, length = 20)
    @NotBlank(message = "Le code rôle est obligatoire")
    private String roleCode;
    
    @Column(name = "role_nom", nullable = false, length = 100)
    @NotBlank(message = "Le nom du rôle est obligatoire")
    private String roleNom;
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "permissions", columnDefinition = "JSON")
    private String permissions; // JSON string pour les permissions détaillées
    
    @Column(name = "actif")
    private Boolean actif = true;
    
    // Relations
    @OneToMany(mappedBy = "role", fetch = FetchType.LAZY)
    private Set<DBUser> users = new HashSet<>();
    
    // Constructeurs
    public Role() {}
    
    public Role(String roleCode, String roleNom) {
        this.roleCode = roleCode;
        this.roleNom = roleNom;
    }
    
    // Getters et Setters
    public Integer getRoleId() { return roleId; }
    public void setRoleId(Integer roleId) { this.roleId = roleId; }
    
    public String getRoleCode() { return roleCode; }
    public void setRoleCode(String roleCode) { this.roleCode = roleCode; }
    
    public String getRoleNom() { return roleNom; }
    public void setRoleNom(String roleNom) { this.roleNom = roleNom; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public String getPermissions() { return permissions; }
    public void setPermissions(String permissions) { this.permissions = permissions; }
    
    public Boolean getActif() { return actif; }
    public void setActif(Boolean actif) { this.actif = actif; }
    
    public Set<DBUser> getUsers() { return users; }
    public void setUsers(Set<DBUser> users) { this.users = users; }
}
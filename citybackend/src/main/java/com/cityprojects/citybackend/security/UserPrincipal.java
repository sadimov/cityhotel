package com.cityprojects.citybackend.security;

import com.cityprojects.citybackend.entity.core.DBUser;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Objects;

/**
 * Principal personnalisé contenant les informations de l'utilisateur connecté
 */
public class UserPrincipal implements UserDetails {
    
    private Long userId;
    private String username;
    private String email;
    private String prenom;
    private String nom;
    private Long hotelId;
    private String hotelCode;
    private String hotelNom;
    private String roleCode;
    private String roleNom;
    private Boolean actif;
    private Boolean compteVerrouille;
    
    @JsonIgnore
    private String password;
    
    private Collection<? extends GrantedAuthority> authorities;

    public UserPrincipal(Long userId, String username, String email, String password,
                        String prenom, String nom, Long hotelId, String hotelCode, String hotelNom,
                        String roleCode, String roleNom, Boolean actif, Boolean compteVerrouille,
                        Collection<? extends GrantedAuthority> authorities) {
        this.userId = userId;
        this.username = username;
        this.email = email;
        this.password = password;
        this.prenom = prenom;
        this.nom = nom;
        this.hotelId = hotelId;
        this.hotelCode = hotelCode;
        this.hotelNom = hotelNom;
        this.roleCode = roleCode;
        this.roleNom = roleNom;
        this.actif = actif;
        this.compteVerrouille = compteVerrouille;
        this.authorities = authorities;
    }

    public static UserPrincipal create(DBUser user, Collection<? extends GrantedAuthority> authorities) {
        return new UserPrincipal(
            user.getUserId(),
            user.getUsername(),
            user.getEmail(),
            user.getPasswordHash(),
            user.getPrenom(),
            user.getNom(),
            user.getHotel().getHotelId(),
            user.getHotel().getHotelCode(),
            user.getHotel().getHotelNom(),
            user.getRole().getRoleCode(),
            user.getRole().getRoleNom(),
            user.getActif(),
            user.getCompteVerrouille(),
            authorities
        );
    }

    // Implémentation de UserDetails
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return !compteVerrouille;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return actif;
    }

    // Getters
    public Long getUserId() { return userId; }
    public String getEmail() { return email; }
    public String getPrenom() { return prenom; }
    public String getNom() { return nom; }
    public Long getHotelId() { return hotelId; }
    public String getHotelCode() { return hotelCode; }
    public String getHotelNom() { return hotelNom; }
    public String getRoleCode() { return roleCode; }
    public String getRoleNom() { return roleNom; }
    public Boolean getActif() { return actif; }
    public Boolean getCompteVerrouille() { return compteVerrouille; }

    public String getNomComplet() {
        return prenom + " " + nom;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserPrincipal that = (UserPrincipal) o;
        return Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId);
    }
}
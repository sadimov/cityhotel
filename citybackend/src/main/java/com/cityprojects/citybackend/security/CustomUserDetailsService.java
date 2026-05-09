package com.cityprojects.citybackend.security;

import com.cityprojects.citybackend.entity.core.DBUser;
import com.cityprojects.citybackend.repository.core.DBUserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

/**
 * Service de chargement des détails utilisateur pour Spring Security
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    @Autowired
    private DBUserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        DBUser user = userRepository.findByUsernameAndActifTrue(username)
                .orElseThrow(() -> new UsernameNotFoundException("Utilisateur non trouvé : " + username));

        // Vérifier si le compte n'est pas verrouillé
        if (user.getCompteVerrouille()) {
            throw new UsernameNotFoundException("Compte verrouillé : " + username);
        }

        // Créer les autorités basées sur le rôle
        List<GrantedAuthority> authorities = Collections.singletonList(
            new SimpleGrantedAuthority("ROLE_" + user.getRole().getRoleCode())
        );

        return UserPrincipal.create(user, authorities);
    }

    /**
     * Charge un utilisateur par son ID
     */
    @Transactional(readOnly = true)
    public UserDetails loadUserById(Long userId) {
        DBUser user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("Utilisateur non trouvé avec l'ID : " + userId));

        if (!user.getActif() || user.getCompteVerrouille()) {
            throw new UsernameNotFoundException("Compte inactif ou verrouillé : " + userId);
        }

        List<GrantedAuthority> authorities = Collections.singletonList(
            new SimpleGrantedAuthority("ROLE_" + user.getRole().getRoleCode())
        );

        return UserPrincipal.create(user, authorities);
    }
}
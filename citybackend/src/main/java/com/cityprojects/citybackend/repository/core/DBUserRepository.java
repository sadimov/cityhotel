package com.cityprojects.citybackend.repository.core;

import com.cityprojects.citybackend.entity.core.DBUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository pour l'entite {@link DBUser}.
 *
 * <h2>POLICY multi-tenant (Tour 7B I4)</h2>
 * <p>Pour TOUTES les methodes acceptant un parametre {@code hotelId} (ou
 * traversant {@code Hotel.hotelId} via une jointure), le caller DOIT fournir
 * une valeur issue de
 * {@link com.cityprojects.citybackend.common.tenant.TenantContext#get()},
 * <b>JAMAIS</b> depuis un payload HTTP, query param, header, ni un body de
 * requete. Le {@code hotelId} client n'est PAS source de verite — seul le JWT
 * (alimente cote serveur a la connexion) l'est.</p>
 *
 * <h3>Etat 2026-05-05</h3>
 * <p>Aucun appel actuel ne viole ce contrat : les callers en
 * {@link com.cityprojects.citybackend.service.auth.AuthService} passent
 * {@code user.getHotel().getHotelId()} issu du DBUser deja charge (origine
 * base, pas client). Toute future methode CRUD {@code listUsers(hotelId)}
 * exposee par un controller DOIT lire le {@code hotelId} via
 * {@code TenantContext.get()} dans la couche service, jamais depuis un
 * {@code @PathVariable} ou {@code @RequestParam}.</p>
 *
 * <h3>Migration cible</h3>
 * <p>Quand le module 'users' metier sera integre, basculer DBUser sur
 * {@code @TenantId} pour beneficier du filtre Hibernate automatique (cf.
 * citybackend/CLAUDE.md section 6.1) et supprimer ces methodes
 * {@code findByHotelHotelId...} qui rendent le filtrage explicite donc
 * vulnerable a un oubli.</p>
 */
@Repository
public interface DBUserRepository extends JpaRepository<DBUser, Long> {
    
    /**
     * Trouve un utilisateur par son nom d'utilisateur
     */
    Optional<DBUser> findByUsername(String username);
    
    /**
     * Trouve un utilisateur par son email
     */
    Optional<DBUser> findByEmail(String email);
    
    /**
     * Trouve un utilisateur par son nom d'utilisateur et actif
     */
    Optional<DBUser> findByUsernameAndActifTrue(String username);
    
    /**
     * Trouve un utilisateur par son email et actif
     */
    Optional<DBUser> findByEmailAndActifTrue(String email);
    
    /**
     * Trouve tous les utilisateurs d'un hôtel
     */
    List<DBUser> findByHotelHotelIdAndActifTrueOrderByNomAscPrenomAsc(Long hotelId);
    
    /**
     * Trouve les utilisateurs par hôtel avec pagination
     */
    Page<DBUser> findByHotelHotelIdAndActifTrue(Long hotelId, Pageable pageable);
    
    /**
     * Trouve les utilisateurs par rôle
     */
    List<DBUser> findByRoleRoleCodeAndActifTrueOrderByNomAscPrenomAsc(String roleCode);
    
    /**
     * Trouve les utilisateurs par hôtel et rôle
     */
    List<DBUser> findByHotelHotelIdAndRoleRoleCodeAndActifTrue(Long hotelId, String roleCode);
    
    /**
     * Recherche d'utilisateurs par nom/prénom dans un hôtel
     */
    @Query("SELECT u FROM DBUser u WHERE u.hotel.hotelId = :hotelId AND " +
           "(LOWER(u.nom) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(u.prenom) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(u.username) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) AND " +
           "u.actif = true")
    Page<DBUser> searchUsersInHotel(@Param("hotelId") Long hotelId, 
                                   @Param("searchTerm") String searchTerm, 
                                   Pageable pageable);
    
    /**
     * Vérifie si un nom d'utilisateur existe déjà
     */
    boolean existsByUsername(String username);
    
    /**
     * Vérifie si un email existe déjà
     */
    boolean existsByEmail(String email);
    
    /**
     * Vérifie si un nom d'utilisateur existe dans un autre utilisateur
     */
    boolean existsByUsernameAndUserIdNot(String username, Long userId);
    
    /**
     * Vérifie si un email existe dans un autre utilisateur
     */
    boolean existsByEmailAndUserIdNot(String email, Long userId);
    
    /**
     * Compte les utilisateurs actifs d'un hôtel
     */
    long countByHotelHotelIdAndActifTrue(Long hotelId);
    
    /**
     * Compte les utilisateurs avec un rôle spécifique dans un hôtel
     */
    long countByHotelHotelIdAndRoleRoleCodeAndActifTrue(Long hotelId, String roleCode);
    
    /**
     * Met à jour la dernière connexion d'un utilisateur
     */
    @Modifying
    @Transactional
    @Query("UPDATE DBUser u SET u.derniereConnexion = :dateConnexion, u.tentativesConnexion = 0 WHERE u.userId = :userId")
    void updateDerniereConnexion(@Param("userId") Long userId, @Param("dateConnexion") LocalDateTime dateConnexion);
    
    /**
     * Incrémente les tentatives de connexion
     */
    @Modifying
    @Transactional
    @Query("UPDATE DBUser u SET u.tentativesConnexion = u.tentativesConnexion + 1 WHERE u.userId = :userId")
    void incrementTentativesConnexion(@Param("userId") Long userId);
    
    /**
     * Verrouille un compte utilisateur
     */
    @Modifying
    @Transactional
    @Query("UPDATE DBUser u SET u.compteVerrouille = true WHERE u.userId = :userId")
    void verrouillerCompte(@Param("userId") Long userId);
    
    /**
     * Déverrouille un compte utilisateur
     */
    @Modifying
    @Transactional
    @Query("UPDATE DBUser u SET u.compteVerrouille = false, u.tentativesConnexion = 0 WHERE u.userId = :userId")
    void deverrouillerCompte(@Param("userId") Long userId);
    
    /**
     * Trouve les comptes verrouillés
     */
    List<DBUser> findByCompteVerrouilleTrueAndActifTrue();
    
    /**
     * Trouve les utilisateurs qui ne se sont pas connectés depuis X jours
     */
    @Query("SELECT u FROM DBUser u WHERE u.derniereConnexion < :dateLimit AND u.actif = true")
    List<DBUser> findUsersNotConnectedSince(@Param("dateLimit") LocalDateTime dateLimit);
}
package com.cityprojects.citybackend.repository.core;

import com.cityprojects.citybackend.entity.core.UserSession;
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
 * Repository pour l'entité UserSession
 */
@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, String> {
    
    /**
     * Trouve une session active par son ID
     */
    Optional<UserSession> findBySessionIdAndActifTrue(String sessionId);
    
    /**
     * Trouve toutes les sessions actives d'un utilisateur
     */
    List<UserSession> findByUserIdAndActifTrueOrderByDerniereActiviteDesc(Long userId);
    
    /**
     * Trouve toutes les sessions actives d'un hôtel
     */
    List<UserSession> findByHotelIdAndActifTrueOrderByDerniereActiviteDesc(Long hotelId);
    
    /**
     * Compte les sessions actives d'un utilisateur
     */
    long countByUserIdAndActifTrue(Long userId);
    
    /**
     * Compte les sessions actives d'un hôtel
     */
    long countByHotelIdAndActifTrue(Long hotelId);
    
    /**
     * Compte le total des sessions actives
     */
    long countByActifTrue();
    
    /**
     * Met à jour l'activité d'une session
     */
    @Modifying
    @Transactional
    @Query("UPDATE UserSession s SET s.derniereActivite = :activite WHERE s.sessionId = :sessionId")
    void updateActivity(@Param("sessionId") String sessionId, @Param("activite") LocalDateTime activite);
    
    /**
     * Désactive une session
     */
    @Modifying
    @Transactional
    @Query("UPDATE UserSession s SET s.actif = false WHERE s.sessionId = :sessionId")
    void deactivateSession(@Param("sessionId") String sessionId);
    
    /**
     * Désactive toutes les sessions d'un utilisateur
     */
    @Modifying
    @Transactional
    @Query("UPDATE UserSession s SET s.actif = false WHERE s.userId = :userId")
    void deactivateUserSessions(@Param("userId") Long userId);
    
    /**
     * Désactive les sessions expirées
     */
    @Modifying
    @Transactional
    @Query("UPDATE UserSession s SET s.actif = false WHERE s.derniereActivite < :dateExpiration AND s.actif = true")
    int deactivateExpiredSessions(@Param("dateExpiration") LocalDateTime dateExpiration);
    
    /**
     * Trouve les sessions expirées
     */
    List<UserSession> findByDerniereActiviteBeforeAndActifTrue(LocalDateTime dateExpiration);
    
    /**
     * Supprime les anciennes sessions (plus de 30 jours)
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM UserSession s WHERE s.dateCreation < :dateLimit")
    int deleteOldSessions(@Param("dateLimit") LocalDateTime dateLimit);
}
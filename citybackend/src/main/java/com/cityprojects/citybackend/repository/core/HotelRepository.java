package com.cityprojects.citybackend.repository.core;

import com.cityprojects.citybackend.entity.core.Hotel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository pour l'entité Hotel
 */
@Repository
public interface HotelRepository extends JpaRepository<Hotel, Long> {
    
    /**
     * Trouve un hôtel par son code unique
     */
    Optional<Hotel> findByHotelCode(String hotelCode);
    
    /**
     * Trouve un hôtel par son code unique et actif
     */
    Optional<Hotel> findByHotelCodeAndActifTrue(String hotelCode);
    
    /**
     * Trouve tous les hôtels actifs
     */
    List<Hotel> findByActifTrueOrderByHotelNom();
    
    /**
     * Recherche d'hôtels par nom (insensible à la casse)
     */
    @Query("SELECT h FROM Hotel h WHERE LOWER(h.hotelNom) LIKE LOWER(CONCAT('%', :nom, '%')) AND h.actif = true")
    Page<Hotel> findByNomContainingIgnoreCase(@Param("nom") String nom, Pageable pageable);
    
    /**
     * Trouve les hôtels par ville
     */
    List<Hotel> findByVilleAndActifTrueOrderByHotelNom(String ville);
    
    /**
     * Trouve les hôtels par pays
     */
    List<Hotel> findByPaysAndActifTrueOrderByHotelNom(String pays);
    
    /**
     * Vérifie si un code hôtel existe déjà
     */
    boolean existsByHotelCode(String hotelCode);
    
    /**
     * Compte le nombre d'hôtels actifs
     */
    long countByActifTrue();
    
    /**
     * Recherche globale d'hôtels
     */
    @Query("SELECT h FROM Hotel h WHERE " +
           "(LOWER(h.hotelNom) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(h.hotelCode) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(h.ville) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) AND " +
           "h.actif = true")
    Page<Hotel> searchHotels(@Param("searchTerm") String searchTerm, Pageable pageable);
}
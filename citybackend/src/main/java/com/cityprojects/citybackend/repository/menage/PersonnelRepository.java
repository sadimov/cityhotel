package com.cityprojects.citybackend.repository.menage;

import com.cityprojects.citybackend.entity.menage.Personnel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repository des agents de menage.
 *
 * <p>Le filtre {@code WHERE hotel_id = ?} est ajoute automatiquement par
 * Hibernate via {@link org.hibernate.annotations.TenantId}. Aucune methode
 * n'accepte donc un {@code hotelId} en parametre.</p>
 */
@Repository
public interface PersonnelRepository
        extends JpaRepository<Personnel, Long>, JpaSpecificationExecutor<Personnel> {

    /** Recherche par numero d'employe (unique par hotel). */
    Optional<Personnel> findByNumeroEmploye(String numeroEmploye);

    /** Verifie l'unicite du numero d'employe (utilise au create). */
    boolean existsByNumeroEmploye(String numeroEmploye);

    /** Verifie l'unicite du numero d'employe en excluant un personnel donne (utilise au update). */
    boolean existsByNumeroEmployeAndPersonnelIdNot(String numeroEmploye, Long personnelId);

    /** Verifie l'unicite de l'email (case-insensitive). */
    boolean existsByEmailIgnoreCase(String email);

    /**
     * Verifie l'unicite de l'email (case-insensitive) en excluant un personnel
     * donne — utilise au update pour eviter de bloquer la maj quand l'email
     * reste identique.
     */
    boolean existsByEmailIgnoreCaseAndPersonnelIdNot(String email, Long personnelId);

    /** Liste des agents actifs tries par prenom puis nom. */
    List<Personnel> findByActifTrueOrderByPrenomAscNomAsc();

    /** Nombre d'agents actifs (utilise par le Dashboard menage — sous-tour C). */
    long countByActifTrue();

    /** Pagination filtree par actif. */
    Page<Personnel> findByActifOrderByPrenomAscNomAsc(Boolean actif, Pageable pageable);

    /** Pagination sans filtre (toutes lignes du tenant). */
    Page<Personnel> findAllByOrderByPrenomAscNomAsc(Pageable pageable);

    /**
     * Recherche textuelle case-insensitive sur prenom, nom, numero, email.
     * Limite aux agents actifs.
     */
    @Query("SELECT p FROM Personnel p WHERE p.actif = true AND ("
            + " LOWER(p.prenom) LIKE LOWER(CONCAT('%', :terme, '%'))"
            + " OR LOWER(p.nom) LIKE LOWER(CONCAT('%', :terme, '%'))"
            + " OR LOWER(p.numeroEmploye) LIKE LOWER(CONCAT('%', :terme, '%'))"
            + " OR (p.email IS NOT NULL AND LOWER(p.email) LIKE LOWER(CONCAT('%', :terme, '%')))"
            + ") ORDER BY p.prenom ASC, p.nom ASC")
    List<Personnel> rechercher(@Param("terme") String terme);

    /** Personnel ayant la specialite donnee dans la chaine libre {@code specialites}. */
    @Query("SELECT p FROM Personnel p WHERE p.actif = true AND "
            + "LOWER(p.specialites) LIKE LOWER(CONCAT('%', :specialite, '%')) "
            + "ORDER BY p.prenom ASC")
    List<Personnel> findBySpecialite(@Param("specialite") String specialite);

    /**
     * Personnel actif ET planifie disponible pour une date donnee
     * (sous-tour menage B1).
     *
     * <p>Croisement {@code Personnel} (actif=true) avec {@code Planning}
     * (dateTravail = :date AND disponible = true). Si un personnel a
     * plusieurs creneaux ce jour-la, il n'apparait qu'une fois (DISTINCT).</p>
     *
     * <p>Multi-tenant : les deux entites sont annotees {@code @TenantId},
     * Hibernate ajoute automatiquement {@code AND p.hotel_id = ? AND pl.hotel_id = ?}.</p>
     */
    @Query("SELECT DISTINCT p FROM Personnel p, Planning pl "
            + "WHERE p.personnelId = pl.personnelId "
            + "AND p.actif = true "
            + "AND pl.dateTravail = :date "
            + "AND pl.disponible = true "
            + "ORDER BY p.prenom ASC, p.nom ASC")
    List<Personnel> findDisponiblesAtDate(@Param("date") LocalDate date);
}

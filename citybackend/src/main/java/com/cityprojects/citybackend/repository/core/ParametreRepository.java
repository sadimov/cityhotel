package com.cityprojects.citybackend.repository.core;

import com.cityprojects.citybackend.entity.core.Parametre;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository pour {@link Parametre}.
 *
 * <h2>Pas de filtre tenant</h2>
 * <p>{@link Parametre} est une entite <b>globale</b> (cf. JavaDoc entite),
 * sans {@code @TenantId}. Les requetes ne sont donc PAS filtrees par
 * Hibernate sur {@code hotel_id}. Les services consommateurs sont
 * volontairement hors {@code @RequireTenant} (administration cross-tenant).</p>
 *
 * <h2>Recherches insensibles a la casse</h2>
 * <p>{@link #findByCleIgnoreCase(String)} et {@link #existsByCleIgnoreCase(String)}
 * traitent les cles comme insensibles a la casse cote application. Le SQL
 * genere par Spring Data JPA fait {@code LOWER(cle) = LOWER(?)}, ce qui
 * est compatible H2 et Postgres mais NON sargable (pas d'index utilise).
 * Volume attendu (qq dizaines de parametres systemes max) : aucun probleme
 * de performance. A reviser si on depasse 1000 entrees.</p>
 */
@Repository
public interface ParametreRepository extends JpaRepository<Parametre, Long> {

    /**
     * Recherche d'un parametre par cle (insensible a la casse).
     */
    Optional<Parametre> findByCleIgnoreCase(String cle);

    /**
     * Test d'existence d'un parametre par cle (insensible a la casse).
     * Utilise pour empecher les doublons a la creation.
     */
    boolean existsByCleIgnoreCase(String cle);

    /**
     * Page des parametres d'une categorie donnee. Categorie {@code null}
     * non geree ici : utiliser {@link #findAll(Pageable)} pour lister tout.
     */
    Page<Parametre> findByCategorie(String categorie, Pageable pageable);
}

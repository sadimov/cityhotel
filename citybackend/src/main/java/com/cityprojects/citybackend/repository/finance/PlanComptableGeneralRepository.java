package com.cityprojects.citybackend.repository.finance;

import com.cityprojects.citybackend.entity.finance.PlanComptableGeneral;
import com.cityprojects.citybackend.entity.finance.StatutCompteComptable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository du Plan Comptable Général SYSCOHADA mauritanien.
 *
 * <p>Reference globale (pas {@code @TenantId}) - aucune isolation tenant
 * Hibernate. Les requetes sont en lecture seule en production : seed via
 * Liquibase ({@code 039-create-plan-comptable-general.xml}).</p>
 */
@Repository
public interface PlanComptableGeneralRepository extends JpaRepository<PlanComptableGeneral, String> {

    /**
     * Recupere les comptes utilisables (de mouvement, pas de regroupement).
     * Utile pour la validation des mappings comptables et pour les listes
     * deroulantes cote front.
     */
    @Query("SELECT p FROM PlanComptableGeneral p "
            + "WHERE p.utilisable = true AND p.statut = :statut "
            + "ORDER BY p.compteCode ASC")
    Page<PlanComptableGeneral> findUtilisables(@Param("statut") StatutCompteComptable statut,
                                               Pageable pageable);

    /** Comptes enfants directs d'un compte parent (hierarchie). */
    List<PlanComptableGeneral> findByParentCodeOrderByCompteCodeAsc(String parentCode);

    /** Comptes d'une classe (1-7). */
    List<PlanComptableGeneral> findByClasseOrderByCompteCodeAsc(Integer classe);

    /**
     * Verifie qu'un code de compte existe et est utilisable (compte feuille,
     * pas un compte de regroupement).
     */
    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN TRUE ELSE FALSE END "
            + "FROM PlanComptableGeneral p "
            + "WHERE p.compteCode = :code "
            + "AND p.utilisable = true "
            + "AND p.statut = com.cityprojects.citybackend.entity.finance.StatutCompteComptable.ACTIF")
    boolean existsUtilisableByCode(@Param("code") String code);

    Optional<PlanComptableGeneral> findByCompteCode(String compteCode);
}

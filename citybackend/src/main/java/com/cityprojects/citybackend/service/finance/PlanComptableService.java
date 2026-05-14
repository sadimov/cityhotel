package com.cityprojects.citybackend.service.finance;

import com.cityprojects.citybackend.dto.finance.PlanComptableGeneralDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service du Plan Comptable Général SYSCOHADA mauritanien.
 *
 * <p>Référentiel global (pas {@code @RequireTenant}) - lectures cross-tenant
 * acceptees. Aucune méthode d'écriture exposée : le PCG est immuable côté
 * application (évolutions par migrations Liquibase).</p>
 */
public interface PlanComptableService {

    /**
     * Liste paginée des comptes du PCG, triée par {@code compteCode} ASC.
     *
     * @param utilisableOnly si {@code true} : ne renvoie que les comptes de
     *                       mouvement (filtre {@code utilisable=true} et
     *                       {@code statut=ACTIF}).
     */
    Page<PlanComptableGeneralDto> findAll(boolean utilisableOnly, Pageable pageable);

    /**
     * Récupère un compte par son code (ex. {@code "411100"}).
     *
     * @throws com.cityprojects.citybackend.exception.ResourceNotFoundException
     *         si le code est inconnu.
     */
    PlanComptableGeneralDto findByCode(String compteCode);
}

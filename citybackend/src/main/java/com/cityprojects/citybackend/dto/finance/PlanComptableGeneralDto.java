package com.cityprojects.citybackend.dto.finance;

import com.cityprojects.citybackend.entity.finance.NatureCompte;
import com.cityprojects.citybackend.entity.finance.SensNormal;
import com.cityprojects.citybackend.entity.finance.StatutCompteComptable;

/**
 * DTO de lecture d'un compte du Plan Comptable Général.
 *
 * <p>Reference globale partagee entre tous les hôtels (pas d'isolation
 * tenant). Lecture seule cote API : aucun POST/PUT/DELETE expose.</p>
 */
public record PlanComptableGeneralDto(
        String compteCode,
        String libelle,
        Integer classe,
        String parentCode,
        NatureCompte nature,
        SensNormal sensNormal,
        boolean utilisable,
        StatutCompteComptable statut
) {}

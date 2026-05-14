package com.cityprojects.citybackend.service.finance;

import com.cityprojects.citybackend.dto.finance.CompteMappingDto;
import com.cityprojects.citybackend.entity.finance.TypeEvenementComptable;

import java.util.List;

/**
 * Service du mapping comptable par hotel.
 *
 * <p>Toutes les opérations sont tenant-scopees ({@code @RequireTenant}).</p>
 */
public interface CompteMappingService {

    /**
     * Renvoie le code de compte PCG associe a un evenement comptable pour
     * l'hotel courant.
     *
     * <p>Strategie de fallback : si l'hotel n'a pas defini de mapping
     * personnalise (ou si le mapping est désactivé), retourne le défaut codé
     * par {@link TypeEvenementComptable#defaultCompteCode()}. Ne lève jamais
     * d'exception : la comptabilité doit fonctionner out-of-the-box pour un
     * hôtel nouvellement créé.</p>
     */
    String getCompte(TypeEvenementComptable type);

    /**
     * Met a jour ou cree le mapping personnalise pour le tenant courant.
     *
     * <p>Validation : le {@code compteCode} doit exister dans le PCG et etre
     * {@code utilisable=true, statut=ACTIF}. Sinon lève
     * {@code BusinessException("error.mapping.invalidCompte")}.</p>
     */
    CompteMappingDto updateMapping(TypeEvenementComptable type, String compteCode);

    /**
     * Liste les mappings de l'hotel courant. Inclut les mappings personnalisés
     * en base ET les défauts codés (avec {@code defaut = true}) pour les
     * événements non encore mappés - exposition complète pour le front admin.
     */
    List<CompteMappingDto> listAll();
}

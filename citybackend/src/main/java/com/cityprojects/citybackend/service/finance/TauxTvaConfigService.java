package com.cityprojects.citybackend.service.finance;

import com.cityprojects.citybackend.dto.finance.TauxTvaConfigDto;
import com.cityprojects.citybackend.entity.finance.TypeServiceTva;

import java.math.BigDecimal;
import java.util.List;

/**
 * Service de configuration TVA par hotel et par type de service (Bloc B4).
 *
 * <p>Toutes les opérations sont tenant-scopees ({@code @RequireTenant}).</p>
 */
public interface TauxTvaConfigService {

    /**
     * Renvoie le taux TVA applicable au tenant courant pour un type de
     * service donné. Fallback sur {@link TypeServiceTva#defaultTaux()} si
     * aucune configuration personnalisée n'existe (ou si elle est désactivée).
     *
     * <p>Ne lève jamais d'exception : un hotel sans configuration a toujours
     * un taux exploitable. C'est ce que les producteurs (FactureServiceImpl,
     * BonCommandeServiceImpl) appellent.</p>
     */
    BigDecimal getTaux(TypeServiceTva typeService);

    /**
     * Liste les configurations TVA du tenant courant. Inclut les valeurs
     * persistées ET les défauts codés (avec {@code defaut = true}) pour les
     * types non encore configurés - exposition complète pour l'UI admin.
     */
    List<TauxTvaConfigDto> findAll();

    /** Lecture par type. Renvoie le DTO synthétique du défaut si non configuré. */
    TauxTvaConfigDto findByType(TypeServiceTva typeService);

    /**
     * Met à jour (upsert) la configuration TVA pour le tenant courant.
     *
     * @param typeService type ciblé.
     * @param taux        taux en pourcentage (0.00 à 99.99).
     * @param actif       optionnel : si null, valeur courante préservée
     *                    (ou {@code true} à la création).
     * @param libelle     optionnel : si null, valeur courante préservée
     *                    (ou {@link TypeServiceTva#defaultLibelle()} à la création).
     */
    TauxTvaConfigDto update(TypeServiceTva typeService, BigDecimal taux,
                            Boolean actif, String libelle);
}

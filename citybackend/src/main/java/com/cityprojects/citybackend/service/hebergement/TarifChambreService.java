package com.cityprojects.citybackend.service.hebergement;

import com.cityprojects.citybackend.dto.hebergement.MontantCalculDto;
import com.cityprojects.citybackend.dto.hebergement.TarifChambreCreateDto;
import com.cityprojects.citybackend.dto.hebergement.TarifChambreDto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Service de tarification saisonniere des chambres (Tour 44 Phase 1).
 *
 * <p>Toutes les methodes operent sous {@code @RequireTenant}.</p>
 *
 * <p>Le calcul {@link #getPrixForDate(Long, LocalDate)} suit la strategie :</p>
 * <ol>
 *   <li>Cherche un tarif actif applicable a la date (priorite DESC) ;</li>
 *   <li>Si trouve : applique {@code prixWeekend} pour samedi/dimanche si non null,
 *       sinon {@code prixNuit} ;</li>
 *   <li>Sinon : fallback sur {@code TypeChambre.prixBase}.</li>
 * </ol>
 */
public interface TarifChambreService {

    /** CRUD basique. */
    TarifChambreDto create(TarifChambreCreateDto dto);

    TarifChambreDto update(Long tarifId, TarifChambreCreateDto dto);

    void delete(Long tarifId);

    TarifChambreDto findById(Long tarifId);

    /** Liste des tarifs actifs d'un type de chambre, dates triees ASC. */
    List<TarifChambreDto> findByType(Long typeId);

    /**
     * Retourne le prix applicable pour une nuit a une date donnee.
     * Fallback sur {@code TypeChambre.prixBase} si aucun tarif saisonnier
     * actif. Leve {@code ResourceNotFoundException} si typeChambre inexistant
     * pour le tenant.
     */
    BigDecimal getPrixForDate(Long typeChambreId, LocalDate date);

    /**
     * Calcule le montant total d'un sejour potentiel : itere sur chaque nuit
     * {@code [dateDebut, dateFin)} et accumule les prix. Retourne le detail
     * jour-par-jour.
     */
    MontantCalculDto calculer(Long typeChambreId, LocalDate dateDebut, LocalDate dateFin);
}

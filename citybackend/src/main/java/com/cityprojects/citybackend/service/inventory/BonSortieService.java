package com.cityprojects.citybackend.service.inventory;

import com.cityprojects.citybackend.dto.inventory.BonSortieCreateDto;
import com.cityprojects.citybackend.dto.inventory.BonSortieDto;
import com.cityprojects.citybackend.entity.inventory.StatutBonSortie;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service de gestion des bons de sortie.
 *
 * <p>Workflow :
 * <ol>
 *   <li>{@link #create(BonSortieCreateDto)} : BS en {@code BROUILLON}.</li>
 *   <li>{@link #valider(Long)} : verifie que les stocks sont disponibles ; passe en {@code VALIDE}.</li>
 *   <li>{@link #livrer(Long)} : decremente le stock ; genere les MouvementStock SORTIE ; passe en {@code LIVRE}.</li>
 *   <li>{@link #annuler(Long)} : ANNULE (seulement si pas encore LIVRE).</li>
 * </ol>
 */
public interface BonSortieService {

    BonSortieDto create(BonSortieCreateDto dto);

    BonSortieDto findById(Long bonSortieId);

    Page<BonSortieDto> findByStatut(StatutBonSortie statut, Pageable pageable);

    BonSortieDto valider(Long bonSortieId);

    BonSortieDto livrer(Long bonSortieId);

    /**
     * Annule un bon de sortie avec motif obligatoire (Tour 51bis).
     *
     * <p>Refus si statut = {@code LIVRE} ({@code error.bonSortie.annulation.statutInvalide}).
     * Le motif est persiste pour audit inventaire / contradictoire stock.</p>
     */
    BonSortieDto annuler(Long bonSortieId, String motif);
}

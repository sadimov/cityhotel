package com.cityprojects.citybackend.service.inventory;

import com.cityprojects.citybackend.dto.inventory.AjustementStockDto;
import com.cityprojects.citybackend.dto.inventory.ProduitCreateDto;
import com.cityprojects.citybackend.dto.inventory.ProduitDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Service de gestion des produits (catalogue + stock).
 *
 * <p>Le {@code stockActuel} evolue uniquement via :
 * <ol>
 *   <li>{@link #ajusterStock(Long, AjustementStockDto)} (ajustement manuel),</li>
 *   <li>la reception d'un BC (cf. {@link BonCommandeService}),</li>
 *   <li>la livraison d'un BS (cf. {@link BonSortieService}).</li>
 * </ol>
 * Toute modification est doublee d'une ecriture {@code MouvementStock}.</p>
 */
public interface ProduitService {

    ProduitDto create(ProduitCreateDto dto);

    ProduitDto update(Long produitId, ProduitCreateDto dto);

    ProduitDto findById(Long produitId);

    Page<ProduitDto> search(String recherche, Long categorieId, Pageable pageable);

    List<ProduitDto> findAllActive();

    /** Liste des produits dont {@code stockActuel <= seuilAlerte}. */
    List<ProduitDto> findEnAlerte();

    /** Liste des produits dont {@code stockActuel <= seuilCritique}. */
    List<ProduitDto> findEnStockCritique();

    /**
     * Ajuste le stock d'un produit (ajustement manuel).
     *
     * <p>Le {@code typeMouvement} doit etre {@code AJUSTEMENT} ou {@code PERTE}.
     * Le delta peut etre positif ou negatif ; le stock final ne peut pas etre &lt; 0.</p>
     */
    ProduitDto ajusterStock(Long produitId, AjustementStockDto dto);

    /**
     * Desactivation (soft delete) : pose {@code actif = false}. Le produit
     * reste en base, n'apparait plus dans les selects {@code /actifs}, mais
     * son historique de mouvements est preserve. Idempotente et reversible.
     */
    void deactivate(Long produitId);

    /**
     * Suppression definitive (hard delete) : retire le produit de la base.
     *
     * <p>Refusee si le produit est reference par au moins un mouvement de
     * stock ({@code error.produit.delete.hasMouvements}) — l'audit trail
     * doit etre preserve. L'appelant doit alors passer par
     * {@link #deactivate}.</p>
     */
    void delete(Long produitId);

    /**
     * Reactivation : pose {@code actif = true} sur un produit precedemment
     * desactive. Symetrique de {@link #deactivate}. Idempotente.
     */
    void reactivate(Long produitId);
}

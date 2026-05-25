package com.cityprojects.citybackend.service.client;

import com.cityprojects.citybackend.dto.client.SocieteCreateDto;
import com.cityprojects.citybackend.dto.client.SocieteDto;
import com.cityprojects.citybackend.dto.client.SocieteUpdateDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Service de gestion des societes (B2B).
 * <p>
 * Toutes les methodes operent dans le tenant courant
 * ({@link com.cityprojects.citybackend.common.tenant.TenantContext}). Aucun
 * parametre {@code hotelId} : Hibernate filtre automatiquement et
 * {@code @RequireTenant} (cote impl) refuse l'appel sans tenant.
 */
public interface SocieteService {

    /**
     * Cree une societe dans le tenant courant.
     *
     * @throws com.cityprojects.citybackend.exception.BusinessException
     *         si le nom (ignore casse) ou le SIRET sont deja utilises.
     */
    SocieteDto create(SocieteCreateDto dto);

    /**
     * Modifie une societe existante.
     *
     * @throws com.cityprojects.citybackend.exception.ResourceNotFoundException
     *         si {@code societeId} n'existe pas dans le tenant.
     * @throws com.cityprojects.citybackend.exception.BusinessException
     *         si le nouveau nom ou SIRET entre en conflit avec une autre societe.
     */
    SocieteDto update(Long societeId, SocieteUpdateDto dto);

    /**
     * Recupere une societe par id (filtree par tenant via Hibernate).
     *
     * @throws com.cityprojects.citybackend.exception.ResourceNotFoundException
     *         si non trouvee dans le tenant courant.
     */
    SocieteDto findById(Long societeId);

    /**
     * Liste des societes actives, triees par nom.
     */
    List<SocieteDto> findAllActive();

    /**
     * Page paginee des societes actives.
     */
    Page<SocieteDto> findAllActive(Pageable pageable);

    /**
     * Recherche libre dans les societes (par nom, ignore casse).
     * Si {@code recherche} est null/vide, retourne la page complete des actives.
     */
    Page<SocieteDto> search(String recherche, Pageable pageable);

    /**
     * Variante de {@link #search(String, Pageable)} permettant d'inclure les
     * societes desactivees dans le resultat. Utilisee par le toggle "Afficher
     * inactives" cote front pour pouvoir les reactiver depuis la liste.
     *
     * @param includeInactive si {@code true}, les societes desactivees sont
     *                        retournees ; sinon comportement identique a
     *                        {@link #search(String, Pageable)}.
     */
    Page<SocieteDto> search(String recherche, boolean includeInactive, Pageable pageable);

    /**
     * Desactive une societe (suppression logique). Refuse si la societe a
     * des clients actifs rattaches.
     */
    void deactivate(Long societeId);

    /**
     * Reactive une societe precedemment desactivee.
     */
    void reactivate(Long societeId);

    /**
     * Suppression definitive. Refuse si des clients actifs sont rattaches.
     */
    void delete(Long societeId);
}

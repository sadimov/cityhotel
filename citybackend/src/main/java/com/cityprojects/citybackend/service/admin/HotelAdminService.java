package com.cityprojects.citybackend.service.admin;

import com.cityprojects.citybackend.dto.admin.HotelAdminDto;
import com.cityprojects.citybackend.dto.admin.HotelCreateAdminDto;
import com.cityprojects.citybackend.dto.admin.HotelUpdateAdminDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service technique de gestion des hotels par un SUPERADMIN.
 *
 * <h2>Service technique cross-tenant</h2>
 * <p>Volontairement <b>hors</b> de l'invariant {@code @RequireTenant} : les
 * hotels sont des objets globaux, pas des entites tenant. La securite est
 * garantie par {@code @PreAuthorize("hasRole('SUPERADMIN')")} cote
 * controller. Toute methode ici peut etre appelee avec un
 * {@link com.cityprojects.citybackend.common.tenant.TenantContext} vide
 * (mode ROOT) ou avec un tenant courant : elle ne touche jamais aux
 * entites tenant.</p>
 *
 * <h2>Soft delete</h2>
 * <p>Pas de DELETE physique : un hotel desactive conserve son historique
 * (factures, users, donnees) qui reste accessible pour audit/comptabilite.
 * Reactivation toujours possible.</p>
 */
public interface HotelAdminService {

    /**
     * Cree un hotel. Le {@code hotelCode} doit etre unique.
     *
     * @throws com.cityprojects.citybackend.exception.BusinessException
     *         si {@code hotelCode} existe deja.
     */
    HotelAdminDto create(HotelCreateAdminDto dto);

    /**
     * Met a jour un hotel existant.
     *
     * @throws com.cityprojects.citybackend.exception.ResourceNotFoundException
     *         si l'hotel n'existe pas.
     */
    HotelAdminDto update(Long hotelId, HotelUpdateAdminDto dto);

    /**
     * Recupere un hotel par id.
     *
     * @throws com.cityprojects.citybackend.exception.ResourceNotFoundException
     *         si l'hotel n'existe pas.
     */
    HotelAdminDto findById(Long hotelId);

    /**
     * Page de tous les hotels (tous statuts, actifs et desactives).
     */
    Page<HotelAdminDto> findAll(Pageable pageable);

    /**
     * Desactive un hotel (soft delete : flag {@code actif=false}).
     * Les donnees restent accessibles via {@link #findById(Long)}.
     */
    void desactiver(Long hotelId);

    /**
     * Reactive un hotel precedemment desactive.
     */
    void reactiver(Long hotelId);
}

package com.cityprojects.citybackend.service.admin;

import com.cityprojects.citybackend.dto.admin.ParametreAdminDto;
import com.cityprojects.citybackend.dto.admin.ParametreCreateAdminDto;
import com.cityprojects.citybackend.dto.admin.ParametreUpdateAdminDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service de gestion des parametres applicatifs globaux.
 *
 * <h2>Service technique cross-tenant</h2>
 * <p>{@link com.cityprojects.citybackend.entity.core.Parametre} n'a pas de
 * {@code hotelId}. Ce service n'a donc pas {@code @RequireTenant}.
 * Securite via {@code @PreAuthorize("hasRole('SUPERADMIN')")} sur le
 * controller.</p>
 *
 * <h2>Restriction "modifiable"</h2>
 * <p>L'update et le delete d'un parametre {@code modifiable=false} sont
 * refuses : {@code BusinessException("error.parametre.notModifiable")}.
 * La creation force toujours {@code modifiable=true} (parametres systeme
 * non creables via API — uniquement via Liquibase).</p>
 *
 * <h2>Cle insensible a la casse</h2>
 * <p>L'unicite de {@code cle} est verifiee insensible a la casse via
 * {@link com.cityprojects.citybackend.repository.core.ParametreRepository#existsByCleIgnoreCase(String)}.
 * On stocke la cle telle que fournie par l'admin (preserve la casse pour
 * lisibilite : ex {@code "app.timezone"}, pas {@code "APP.TIMEZONE"}).</p>
 */
public interface ParametreAdminService {

    /**
     * Cree un parametre. La cle doit etre unique (insensible a la casse).
     * {@code modifiable} est force a {@code true}.
     *
     * @throws com.cityprojects.citybackend.exception.BusinessException
     *         si la cle existe deja.
     */
    ParametreAdminDto create(ParametreCreateAdminDto dto);

    /**
     * Met a jour un parametre. Refuse si {@code modifiable=false}.
     *
     * @throws com.cityprojects.citybackend.exception.ResourceNotFoundException
     *         si le parametre n'existe pas.
     * @throws com.cityprojects.citybackend.exception.BusinessException
     *         {@code "error.parametre.notModifiable"} si protege.
     */
    ParametreAdminDto update(Long parametreId, ParametreUpdateAdminDto dto);

    /**
     * Supprime un parametre. Refuse si {@code modifiable=false}.
     */
    void delete(Long parametreId);

    /**
     * Recupere un parametre par id.
     */
    ParametreAdminDto findById(Long parametreId);

    /**
     * Recupere un parametre par cle (insensible a la casse).
     */
    ParametreAdminDto findByCle(String cle);

    /**
     * Page des parametres (toutes categories).
     */
    Page<ParametreAdminDto> findAll(Pageable pageable);

    /**
     * Page des parametres d'une categorie donnee.
     */
    Page<ParametreAdminDto> findByCategorie(String categorie, Pageable pageable);
}

package com.cityprojects.citybackend.service.menage;

import com.cityprojects.citybackend.dto.menage.PersonnelCreateDto;
import com.cityprojects.citybackend.dto.menage.PersonnelDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;

/**
 * Service de gestion du personnel de menage.
 *
 * <p>Toutes les methodes operent sur le tenant courant (resolution via
 * {@link com.cityprojects.citybackend.common.tenant.TenantContext}).</p>
 */
public interface PersonnelService {

    /** Cree un nouveau personnel. {@code numeroEmploye} doit etre unique par hotel. */
    PersonnelDto create(PersonnelCreateDto dto);

    /** Modifie un personnel existant. */
    PersonnelDto update(Long personnelId, PersonnelCreateDto dto);

    /** Recupere un personnel par son ID. */
    PersonnelDto findById(Long personnelId);

    /** Recupere un personnel par son numero d'employe. */
    PersonnelDto findByNumero(String numeroEmploye);

    /** Liste des personnels actifs. */
    List<PersonnelDto> findAllActive();

    /** Pagination filtree par actif. */
    Page<PersonnelDto> findAll(Boolean actif, Pageable pageable);

    /** Recherche textuelle (prenom/nom/numero/email). */
    List<PersonnelDto> search(String terme);

    /** Recherche par specialite. */
    List<PersonnelDto> findBySpecialite(String specialite);

    /** Desactive un personnel (refuse si taches en cours). */
    void deactivate(Long personnelId);

    /** Reactive un personnel desactive. */
    void reactivate(Long personnelId);

    /**
     * Personnel actif ET planifie disponible pour une date donnee
     * (sous-tour menage B1).
     *
     * <p>Les criteres combines :</p>
     * <ul>
     *   <li>{@code Personnel.actif = true}</li>
     *   <li>Au moins un creneau {@code Planning} pour cette date avec
     *       {@code Planning.disponible = true}</li>
     * </ul>
     *
     * <p>Si {@code date} est {@code null}, le service utilise
     * {@code LocalDate.now()} (TZ serveur via {@link java.time.Clock}).</p>
     *
     * <p>Endpoint cible : {@code GET /api/menage/personnel/disponibles?date=...}
     * (cf. {@code endpoints_module_menage.txt} ligne 41).</p>
     */
    List<PersonnelDto> findDisponibles(LocalDate date);
}

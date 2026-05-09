package com.cityprojects.citybackend.service.client;

import com.cityprojects.citybackend.dto.client.ClientCreateDto;
import com.cityprojects.citybackend.dto.client.ClientDto;
import com.cityprojects.citybackend.dto.client.ClientUpdateDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service de gestion des clients (personnes physiques).
 * <p>
 * Toutes les methodes operent dans le tenant courant ; aucun parametre
 * {@code hotelId} (Hibernate filtre via {@code @TenantId} et {@code @RequireTenant}
 * cote impl bloque les appels sans contexte).
 * <p>
 * La generation du {@code numeroClient} est deleguee a
 * {@link com.cityprojects.citybackend.service.finance.NumerotationService}
 * (type {@link com.cityprojects.citybackend.service.finance.TypeNumerotation#CLI}).
 */
public interface ClientService {

    /**
     * Cree un client. Genere automatiquement {@code numeroClient} (CLI-...).
     *
     * @throws com.cityprojects.citybackend.exception.BusinessException
     *         si l'email (ignore casse) est deja utilise dans le tenant,
     *         ou si la societe referencee est inactive.
     * @throws com.cityprojects.citybackend.exception.ResourceNotFoundException
     *         si la {@code societeId} fournie n'existe pas dans le tenant.
     */
    ClientDto create(ClientCreateDto dto);

    /**
     * Met a jour un client existant.
     */
    ClientDto update(Long clientId, ClientUpdateDto dto);

    /**
     * Recupere un client par id (filtre tenant via Hibernate).
     */
    ClientDto findById(Long clientId);

    /**
     * Recupere un client par numero metier (CLI-2026-MR-000123).
     */
    ClientDto findByNumeroClient(String numeroClient);

    /**
     * Page des clients actifs.
     * <p>
     * Pas de variante {@code List<ClientDto> findAllActive()} : ecartee au
     * Tour 9bis (dead code, aucune route HTTP exposee, risque OOM si volumes
     * importants). Toujours utiliser cette variante paginee.
     */
    Page<ClientDto> findAllActive(Pageable pageable);

    /**
     * Page des clients d'une societe donnee (tous statuts).
     */
    Page<ClientDto> findBySociete(Long societeId, Pageable pageable);

    /**
     * Page des clients actifs sans societe (B2C).
     */
    Page<ClientDto> findWithoutSociete(Pageable pageable);

    /**
     * Recherche libre (nom, prenom, email, numero_client).
     */
    Page<ClientDto> search(String recherche, Pageable pageable);

    /**
     * Rattache un client a une societe. Passer {@code societeId=null}
     * pour dissocier.
     */
    ClientDto assignToSociete(Long clientId, Long societeId);

    /**
     * Desactive un client (suppression logique).
     */
    void deactivate(Long clientId);

    /**
     * Reactive un client.
     */
    void reactivate(Long clientId);
}

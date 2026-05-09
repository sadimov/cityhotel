package com.cityprojects.citybackend.service.client;

import com.cityprojects.citybackend.common.tenant.RequireTenant;
import com.cityprojects.citybackend.dto.client.ClientCreateDto;
import com.cityprojects.citybackend.dto.client.ClientDto;
import com.cityprojects.citybackend.dto.client.ClientUpdateDto;
import com.cityprojects.citybackend.entity.client.Client;
import com.cityprojects.citybackend.entity.client.Societe;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.exception.ResourceNotFoundException;
import com.cityprojects.citybackend.mapper.client.ClientMapper;
import com.cityprojects.citybackend.repository.client.ClientRepository;
import com.cityprojects.citybackend.repository.client.SocieteRepository;
import com.cityprojects.citybackend.service.finance.NumerotationService;
import com.cityprojects.citybackend.service.finance.TypeNumerotation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Implementation de {@link ClientService}.
 * <p>
 * Conventions appliquees (cf. citybackend/CLAUDE.md §3.3) :
 * <ul>
 *   <li>{@code @RequireTenant} au niveau classe.</li>
 *   <li>{@code @Transactional(readOnly = true)} a la classe, override en ecriture.</li>
 *   <li>Constructeur explicite (pas de Lombok en palier 1).</li>
 *   <li>Aucun {@code setHotelId} : Hibernate populate via le resolver.</li>
 *   <li>{@code numeroClient} genere via {@link NumerotationService} (cle CLI).</li>
 * </ul>
 */
@Service
@RequireTenant
@Transactional(readOnly = true)
public class ClientServiceImpl implements ClientService {

    private static final Logger logger = LoggerFactory.getLogger(ClientServiceImpl.class);

    private final ClientRepository clientRepository;
    private final SocieteRepository societeRepository;
    private final ClientMapper clientMapper;
    private final NumerotationService numerotationService;

    public ClientServiceImpl(ClientRepository clientRepository,
                             SocieteRepository societeRepository,
                             ClientMapper clientMapper,
                             NumerotationService numerotationService) {
        this.clientRepository = clientRepository;
        this.societeRepository = societeRepository;
        this.clientMapper = clientMapper;
        this.numerotationService = numerotationService;
    }

    /**
     * Cree un nouveau client.
     *
     * <h2>Semantique de l'email</h2>
     * <p>L'unicite de l'email (case-insensitive) est verifiee sur <b>tous</b> les
     * clients du tenant, y compris les inactifs (desactives). Cela signifie qu'un
     * email reste <b>reserve</b> meme apres desactivation : si un client revient,
     * on doit le reactiver (ne pas en creer un nouveau). Cette regle evite la
     * confusion historique facture/reservation.</p>
     *
     * <p><b>Attention :</b> la contrainte est <b>app-only</b> (pas de UNIQUE SQL).
     * Sous concurrence, deux INSERT simultanes peuvent passer la verification.
     * Mitigation acceptee : faible probabilite, ecart detecte en revue manuelle.
     * A durcir avec un partial index Postgres
     * {@code UNIQUE (hotel_id, lower(email))} si l'ecart devient un probleme.</p>
     */
    @Override
    @Transactional
    public ClientDto create(ClientCreateDto dto) {
        logger.info("Creation client : prenom={}, nom={}", dto.prenom(), dto.nom());

        // Email unique (ignore casse) dans le tenant
        if (dto.email() != null && !dto.email().isBlank()
                && clientRepository.existsByEmailIgnoreCase(dto.email())) {
            throw new BusinessException("error.client.email.alreadyExists");
        }

        // Validation societe (existe + appartient au meme tenant via filtre Hibernate + active)
        if (dto.societeId() != null) {
            Societe societe = societeRepository.findById(dto.societeId())
                    .orElseThrow(() -> new ResourceNotFoundException("error.societe.notFound"));
            if (!Boolean.TRUE.equals(societe.getActif())) {
                throw new BusinessException("error.client.societe.inactive");
            }
        }

        Client entity = clientMapper.toEntity(dto);
        entity.setActif(Boolean.TRUE);
        // Generation numero metier (CLI-2026-MR-000123 par exemple) — sequence par hotel/exercice
        entity.setNumeroClient(numerotationService.next(TypeNumerotation.CLI));
        // PAS de setHotelId : Hibernate s'en charge.

        Client saved = clientRepository.save(entity);
        logger.info("Client cree : id={}, numero={}", saved.getClientId(), saved.getNumeroClient());
        return clientMapper.toDto(saved);
    }

    @Override
    @Transactional
    public ClientDto update(Long clientId, ClientUpdateDto dto) {
        logger.info("Modification client id={}", clientId);

        Client entity = clientRepository.findById(clientId)
                .orElseThrow(() -> new ResourceNotFoundException("error.client.notFound"));

        // Email unique (exclure le client courant)
        if (dto.email() != null && !dto.email().isBlank()) {
            Optional<Client> byEmail = clientRepository.findByEmailIgnoreCase(dto.email());
            if (byEmail.isPresent() && !byEmail.get().getClientId().equals(clientId)) {
                throw new BusinessException("error.client.email.alreadyExists");
            }
        }

        // Validation societe (si rattachement modifie)
        if (dto.societeId() != null) {
            Societe societe = societeRepository.findById(dto.societeId())
                    .orElseThrow(() -> new ResourceNotFoundException("error.societe.notFound"));
            if (!Boolean.TRUE.equals(societe.getActif())) {
                throw new BusinessException("error.client.societe.inactive");
            }
        }

        clientMapper.updateEntity(entity, dto);
        Client saved = clientRepository.save(entity);
        return clientMapper.toDto(saved);
    }

    @Override
    public ClientDto findById(Long clientId) {
        Client entity = clientRepository.findById(clientId)
                .orElseThrow(() -> new ResourceNotFoundException("error.client.notFound"));
        return clientMapper.toDto(entity);
    }

    @Override
    public ClientDto findByNumeroClient(String numeroClient) {
        Client entity = clientRepository.findByNumeroClient(numeroClient)
                .orElseThrow(() -> new ResourceNotFoundException("error.client.notFound"));
        return clientMapper.toDto(entity);
    }

    @Override
    public Page<ClientDto> findAllActive(Pageable pageable) {
        return clientRepository.findByActifTrueOrderByNomAscPrenomAsc(pageable)
                .map(clientMapper::toDto);
    }

    @Override
    public Page<ClientDto> findBySociete(Long societeId, Pageable pageable) {
        return clientRepository.findBySocieteIdOrderByNomAscPrenomAsc(societeId, pageable)
                .map(clientMapper::toDto);
    }

    @Override
    public Page<ClientDto> findWithoutSociete(Pageable pageable) {
        return clientRepository.findBySocieteIdIsNullAndActifTrueOrderByNomAscPrenomAsc(pageable)
                .map(clientMapper::toDto);
    }

    @Override
    public Page<ClientDto> search(String recherche, Pageable pageable) {
        if (recherche == null || recherche.isBlank()) {
            return findAllActive(pageable);
        }
        return clientRepository.searchClients(recherche.trim(), pageable)
                .map(clientMapper::toDto);
    }

    @Override
    @Transactional
    public ClientDto assignToSociete(Long clientId, Long societeId) {
        logger.info("Rattachement client {} -> societe {}", clientId, societeId);

        Client entity = clientRepository.findById(clientId)
                .orElseThrow(() -> new ResourceNotFoundException("error.client.notFound"));

        if (societeId != null) {
            Societe societe = societeRepository.findById(societeId)
                    .orElseThrow(() -> new ResourceNotFoundException("error.societe.notFound"));
            if (!Boolean.TRUE.equals(societe.getActif())) {
                throw new BusinessException("error.client.societe.inactive");
            }
        }

        entity.setSocieteId(societeId);
        Client saved = clientRepository.save(entity);
        return clientMapper.toDto(saved);
    }

    @Override
    @Transactional
    public void deactivate(Long clientId) {
        logger.info("Desactivation client id={}", clientId);
        Client entity = clientRepository.findById(clientId)
                .orElseThrow(() -> new ResourceNotFoundException("error.client.notFound"));
        // TODO[tour-hebergement] : refuser la desactivation si reservations actives sur ce client
        // TODO[tour-finance-2]   : refuser la desactivation si factures impayees sur ce client
        //                          (factureRepository.existsByClientIdAndStatutIn(EMISE, PARTIELLEMENT_PAYEE))
        entity.setActif(Boolean.FALSE);
        clientRepository.save(entity);
    }

    @Override
    @Transactional
    public void reactivate(Long clientId) {
        logger.info("Reactivation client id={}", clientId);
        Client entity = clientRepository.findById(clientId)
                .orElseThrow(() -> new ResourceNotFoundException("error.client.notFound"));
        entity.setActif(Boolean.TRUE);
        clientRepository.save(entity);
    }
}
